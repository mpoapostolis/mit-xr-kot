package gr.impatron.xr.ar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Quaternion
import gr.impatron.xr.ARPack
import gr.impatron.xr.ARSceneData
import gr.impatron.xr.ARTimelineEvent
import gr.impatron.xr.PB
import gr.impatron.xr.TimelineKind
import android.graphics.SurfaceTexture
import android.view.Surface
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Stream
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.texture.VideoTexture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live AR controller. Each detected card is mirrored by a [Node] that follows
 * `img.centerPose` every frame, so when the user moves the camera or the card,
 * the AR content reposisitons in real time instead of staying frozen at the
 * pose where the image was first detected.
 *
 * Lifecycle:
 *  - FULL_TRACKING (image currently visible): show node, mount events
 *  - LAST_KNOWN_POSE (image was just visible): hide node, pause audio
 *  - STOPPED (gone): destroy node, stop audio
 */
class ARSceneController(
    private val sceneView: ARSceneView,
    private val context: Context,
    private val pack: ARPack,
    private val onStatusChanged: (String) -> Unit,
    private val onSceneFound: (name: String, subtitle: String?) -> Unit,
    private val onSceneLost: () -> Unit,
) {
    companion object {
        private const val TAG = "ARSceneController"
        private const val CARD_PHYSICAL_WIDTH = 0.2f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private data class CardEntry(
        val scene: ARSceneData,
        val root: Node,
        var mounted: Boolean = false,
        var visible: Boolean = false,
    )

    private val entries = mutableMapOf<String, CardEntry>()
    private val audioPlayers = mutableMapOf<String, android.media.MediaPlayer>()
    private var activeCardId: String? = null

    fun attach() {
        sceneView.planeRenderer.isEnabled = false
        sceneView.configureSession { _, config ->
            config.focusMode = Config.FocusMode.AUTO
            // ENVIRONMENTAL_HDR gives ARCore-estimated IBL + main directional
            // light so PBR materials on GLBs aren't pitch black.
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }

        addSunLight()

        sceneView.onSessionUpdated = { _, frame ->
            val updated = frame.getUpdatedTrackables(AugmentedImage::class.java)
            for (img in updated) onImageUpdate(img)
        }

        onStatusChanged("Κατέβασμα markers…")
        scope.launch { setupTracking() }
    }

    private suspend fun setupTracking() {
        try {
            val session = sceneView.session ?: run {
                onStatusChanged("AR session δεν διαθέσιμη")
                return
            }
            val database = AugmentedImageDatabase(session)
            var added = 0
            for (scene in pack.scenes) {
                val url = scene.card.markerUrl ?: continue
                try {
                    val bytes = PB.download(url)
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        val idx = database.addImage(scene.card.id, bitmap, CARD_PHYSICAL_WIDTH)
                        Log.i(TAG, "marker '${scene.card.id}' index=$idx")
                        added++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "marker download/decode failed for ${scene.card.id}", e)
                }
            }
            val config = session.config
            config.augmentedImageDatabase = database
            session.configure(config)
            Log.i(TAG, "tracking configured with $added markers")
            onStatusChanged(if (added > 0) "Σάρωσε μια κάρτα" else "Δεν φορτώθηκαν markers")
        } catch (e: Exception) {
            Log.e(TAG, "setupTracking failed", e)
            onStatusChanged("Σφάλμα: ${e.message}")
        }
    }

    private fun onImageUpdate(img: AugmentedImage) {
        val scene = pack.scenes.firstOrNull { it.card.id == img.name } ?: return
        when (img.trackingState) {
            TrackingState.TRACKING -> {
                ensureEntry(scene)
                val entry = entries[scene.card.id] ?: return
                if (img.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING) {
                    applyPose(entry.root, img.centerPose)
                    if (!entry.visible) {
                        entry.root.isVisible = true
                        entry.visible = true
                        resumeAudioFor(scene)
                    }
                    if (activeCardId != scene.card.id) {
                        activeCardId = scene.card.id
                        onSceneFound(scene.card.name, scene.card.subtitle)
                    }
                } else {
                    // LAST_KNOWN_POSE — image not currently visible
                    if (entry.visible) {
                        entry.root.isVisible = false
                        entry.visible = false
                        pauseAudioFor(scene)
                        if (activeCardId == scene.card.id) {
                            activeCardId = null
                            onSceneLost()
                        }
                    }
                }
            }
            TrackingState.PAUSED -> {
                val entry = entries[scene.card.id] ?: return
                if (entry.visible) {
                    entry.root.isVisible = false
                    entry.visible = false
                    pauseAudioFor(scene)
                    if (activeCardId == scene.card.id) {
                        activeCardId = null
                        onSceneLost()
                    }
                }
            }
            TrackingState.STOPPED -> destroyEntry(scene.card.id)
            else -> {}
        }
    }

    private fun ensureEntry(scene: ARSceneData) {
        if (entries.containsKey(scene.card.id)) return
        val root = Node(sceneView.engine).apply {
            name = "card-${scene.card.id}"
            isVisible = false
        }
        sceneView.addChildNode(root)
        val entry = CardEntry(scene = scene, root = root)
        entries[scene.card.id] = entry
        scope.launch { mountEvents(entry) }
        Log.i(TAG, "entry created for ${scene.card.id}")
    }

    private suspend fun mountEvents(entry: CardEntry) {
        if (entry.mounted) return
        entry.mounted = true
        try {
            placeOutline(entry.root)
            for (event in entry.scene.events) {
                placeEvent(event, entry.root)
            }
        } catch (e: Exception) {
            Log.e(TAG, "mountEvents failed", e)
        }
    }

    private fun applyPose(node: Node, pose: Pose) {
        node.position = Position(pose.tx(), pose.ty(), pose.tz())
        node.quaternion = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw())
    }

    private suspend fun placeEvent(event: ARTimelineEvent, parent: Node) {
        Log.i(TAG, "placing event=${event.id} kind=${event.kind} asset=${event.asset?.name}")
        try {
            when (event.kind) {
                TimelineKind.MODEL -> placeModel(event, parent)
                TimelineKind.IMAGE -> placeImageQuad(event, parent)
                TimelineKind.TEXT -> placeTextQuad(event, parent)
                TimelineKind.AUDIO -> placeAudio(event, parent)
                TimelineKind.VIDEO -> placeVideoPlaceholder(event, parent)
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "placeEvent failed for ${event.id}", e)
        }
    }

    private suspend fun placeModel(event: ARTimelineEvent, parent: Node) {
        val url = event.asset?.fileUrl ?: return
        Log.i(TAG, "downloading model $url")
        // Download to local file first — large GLBs (60+ MB) over HTTP through
        // SceneView's loadModelInstance occasionally hang, and createModelInstance
        // on a File handle is robust regardless of size.
        val cacheFile = File(context.cacheDir, "model_${event.id}.glb")
        try {
            val bytes = withContext(Dispatchers.IO) { PB.download(url) }
            withContext(Dispatchers.IO) { cacheFile.writeBytes(bytes) }
            Log.i(TAG, "downloaded ${bytes.size} bytes to ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "model download failed for $url", e)
            return
        }
        val instance = runCatching {
            sceneView.modelLoader.createModelInstance(cacheFile)
        }.onFailure { Log.e(TAG, "model createModelInstance failed for ${event.id}", it) }
            .getOrNull()
        if (instance == null) {
            Log.w(TAG, "model createModelInstance null for ${event.id}")
            return
        }
        Log.i(TAG, "model loaded ${event.id}")
        val node = ModelNode(
            modelInstance = instance,
            autoAnimate = event.autoplay,
            scaleToUnits = CARD_PHYSICAL_WIDTH * event.scale.x.coerceAtLeast(0.1f),
        ).apply {
            position = Position(
                event.position.x * CARD_PHYSICAL_WIDTH,
                event.position.y.coerceAtLeast(0.001f) * CARD_PHYSICAL_WIDTH,
                event.position.z * CARD_PHYSICAL_WIDTH,
            )
        }
        parent.addChildNode(node)
        Log.i(TAG, "model added at ${node.position}")
    }

    private suspend fun placeImageQuad(event: ARTimelineEvent, parent: Node) {
        val url = event.asset?.fileUrl ?: return
        val pngBytes = withContext(Dispatchers.IO) { PB.download(url) }
        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        }
        val aspect = if (bmp != null) bmp.width.toFloat() / bmp.height else 1f
        bmp?.recycle()
        val glb = withContext(Dispatchers.Default) { GltfQuad.build(pngBytes, aspect) }
        placeQuad(glb, "image_${event.id}", event, parent)
    }

    private suspend fun placeTextQuad(event: ARTimelineEvent, parent: Node) {
        val txt = event.text ?: return
        if (txt.isBlank()) return
        val rendered = withContext(Dispatchers.Default) { TextPng.render(txt) }
        val glb = withContext(Dispatchers.Default) {
            GltfQuad.build(rendered.bytes, rendered.aspect)
        }
        placeQuad(glb, "text_${event.id}", event, parent)
    }

    private suspend fun placeAudio(event: ARTimelineEvent, parent: Node) {
        val url = event.asset?.fileUrl ?: return
        startMediaPlayer(event.id, url, event.loop, event.autoplay)
        val rendered = withContext(Dispatchers.Default) {
            TextPng.render("🔊  ${event.asset?.name ?: "Audio"}")
        }
        val glb = withContext(Dispatchers.Default) {
            GltfQuad.build(rendered.bytes, rendered.aspect)
        }
        placeQuad(glb, "audio_${event.id}", event, parent)
    }

    private data class VideoRig(
        val surface: Surface,
        val surfaceTexture: SurfaceTexture,
        val stream: Stream,
        val texture: com.google.android.filament.Texture,
    )

    private val videoRigs = mutableMapOf<String, VideoRig>()

    private suspend fun placeVideoPlaceholder(event: ARTimelineEvent, parent: Node) {
        val url = event.asset?.fileUrl ?: return
        try {
            // 1. SurfaceTexture acts as the bridge between MediaPlayer and
            //    Filament: MediaPlayer renders into its Surface, Filament reads
            //    via Stream.
            val surfaceTex = SurfaceTexture(0).also { it.detachFromGLContext() }
            val surface = Surface(surfaceTex)
            val stream = Stream.Builder().stream(surfaceTex).build(sceneView.engine)
            val texture = VideoTexture.Builder().stream(stream).build(sceneView.engine)
            videoRigs[event.id] = VideoRig(surface, surfaceTex, stream, texture)

            // 2. Material instance pointing to the streaming texture
            val matInstance = sceneView.materialLoader.createVideoInstance(texture, null)

            // 3. Plane with that material (start 16:9; we'll resize when video size known)
            val aspect = 16f / 9f
            val w = event.scale.x * CARD_PHYSICAL_WIDTH
            val plane = PlaneNode(
                engine = sceneView.engine,
                size = Float3(w, w / aspect, 0.001f),
                center = Float3(0f, 0f, 0f),
                normal = Float3(0f, 0f, 1f),
                uvScale = Float2(1f, 1f),
                materialInstance = matInstance,
            ).apply {
                position = Position(
                    event.position.x * CARD_PHYSICAL_WIDTH,
                    event.position.y.coerceAtLeast(0.001f) * CARD_PHYSICAL_WIDTH,
                    event.position.z * CARD_PHYSICAL_WIDTH,
                )
                rotation = Rotation(-90f, 0f, 0f)
            }
            parent.addChildNode(plane)

            // 4. MediaPlayer → surface
            val mp = android.media.MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setDataSource(url)
                isLooping = event.loop
                setSurface(surface)
                setOnPreparedListener { if (event.autoplay) start() }
                prepareAsync()
            }
            audioPlayers[event.id] = mp
            Log.i(TAG, "video plane created for $url")
        } catch (e: Exception) {
            Log.e(TAG, "video plane failed for $url", e)
        }
    }

    private suspend fun placeOutline(parent: Node) {
        try {
            val png = withContext(Dispatchers.Default) { OutlinePng.render() }
            val glb = withContext(Dispatchers.Default) { GltfQuad.build(png, aspect = 1f) }
            val file = File(context.cacheDir, "outline.glb")
            withContext(Dispatchers.IO) { file.writeBytes(glb) }
            val instance = sceneView.modelLoader.createModelInstance(file)
            if (instance == null) {
                Log.w(TAG, "outline createModelInstance returned null")
                return
            }
            val node = ModelNode(modelInstance = instance, scaleToUnits = null).apply {
                position = Position(0f, 0.001f, 0f)
                rotation = Rotation(-90f, 0f, 0f)
                scale = Scale(
                    CARD_PHYSICAL_WIDTH,
                    CARD_PHYSICAL_WIDTH,
                    CARD_PHYSICAL_WIDTH,
                )
            }
            parent.addChildNode(node)
        } catch (e: Exception) {
            Log.w(TAG, "outline failed", e)
        }
    }

    private suspend fun placeQuad(
        glbBytes: ByteArray,
        cacheName: String,
        event: ARTimelineEvent,
        parent: Node,
    ) {
        val cacheFile = File(context.cacheDir, "$cacheName.glb")
        withContext(Dispatchers.IO) { cacheFile.writeBytes(glbBytes) }
        val instance = runCatching {
            sceneView.modelLoader.createModelInstance(cacheFile)
        }.onFailure { Log.e(TAG, "quad createModelInstance failed for $cacheName", it) }
            .getOrNull()
        if (instance == null) {
            Log.w(TAG, "quad createModelInstance null for $cacheName")
            return
        }
        Log.i(TAG, "quad loaded $cacheName (${cacheFile.length()} bytes)")

        val node = ModelNode(modelInstance = instance, scaleToUnits = null).apply {
            position = Position(
                event.position.x * CARD_PHYSICAL_WIDTH,
                event.position.y.coerceAtLeast(0.001f) * CARD_PHYSICAL_WIDTH,
                event.position.z * CARD_PHYSICAL_WIDTH,
            )
            rotation = Rotation(-90f, 0f, 0f)
            scale = Scale(
                event.scale.x * CARD_PHYSICAL_WIDTH,
                event.scale.y * CARD_PHYSICAL_WIDTH,
                event.scale.z * CARD_PHYSICAL_WIDTH,
            )
        }
        parent.addChildNode(node)
    }

    // ---------- Audio ----------

    private fun startMediaPlayer(id: String, url: String, loop: Boolean, autoplay: Boolean) {
        try {
            val mp = android.media.MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setDataSource(url)
                isLooping = loop
                setOnPreparedListener { if (autoplay) start() }
                prepareAsync()
            }
            audioPlayers[id] = mp
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer for $id failed", e)
        }
    }

    private fun pauseAudioFor(scene: ARSceneData) {
        for (event in scene.events) {
            val mp = audioPlayers[event.id] ?: continue
            try { if (mp.isPlaying) mp.pause() } catch (_: Exception) {}
        }
    }

    private fun resumeAudioFor(scene: ARSceneData) {
        for (event in scene.events) {
            if (event.kind != TimelineKind.AUDIO && event.kind != TimelineKind.VIDEO) continue
            val mp = audioPlayers[event.id] ?: continue
            try { if (!mp.isPlaying) mp.start() } catch (_: Exception) {}
        }
    }

    private fun stopAllAudio() {
        for ((_, mp) in audioPlayers) {
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
        audioPlayers.clear()
    }

    private fun destroyEntry(cardId: String) {
        val entry = entries.remove(cardId) ?: return
        // Stop audio bound to this card
        for (event in entry.scene.events) {
            audioPlayers.remove(event.id)?.let { mp ->
                try { if (mp.isPlaying) mp.stop(); mp.release() } catch (_: Exception) {}
            }
        }
        entry.root.destroy()
        if (activeCardId == cardId) {
            activeCardId = null
            onSceneLost()
        }
        Log.i(TAG, "destroyed entry $cardId")
    }

    private var sunEntity: Int = 0
    private var fillEntity: Int = 0

    private fun addSunLight() {
        try {
            val engine = sceneView.engine
            // Sun-like directional light from above-front-left.
            sunEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.95f, 0.88f)
                .intensity(120_000f) // lux, sun-like
                .direction(-0.3f, -1f, -0.4f)
                .castShadows(false)
                .build(engine, sunEntity)
            sceneView.scene.addEntity(sunEntity)

            // Cool fill light from the opposite side to lift shadows.
            fillEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.75f, 0.82f, 1.0f)
                .intensity(50_000f)
                .direction(0.6f, -0.4f, 0.5f)
                .castShadows(false)
                .build(engine, fillEntity)
            sceneView.scene.addEntity(fillEntity)
            Log.i(TAG, "sun + fill lights added")
        } catch (e: Exception) {
            Log.w(TAG, "failed to add directional lights", e)
        }
    }

    fun detach() {
        stopAllAudio()
        for (rig in videoRigs.values) try {
            rig.surface.release()
            rig.surfaceTexture.release()
            sceneView.engine.destroyStream(rig.stream)
            sceneView.engine.destroyTexture(rig.texture)
        } catch (_: Exception) {}
        videoRigs.clear()
        for (entry in entries.values) entry.root.destroy()
        entries.clear()
        try {
            if (sunEntity != 0) {
                sceneView.scene.removeEntity(sunEntity)
                sceneView.engine.lightManager.destroy(sunEntity)
                EntityManager.get().destroy(sunEntity)
            }
            if (fillEntity != 0) {
                sceneView.scene.removeEntity(fillEntity)
                sceneView.engine.lightManager.destroy(fillEntity)
                EntityManager.get().destroy(fillEntity)
            }
        } catch (_: Exception) {}
        scope.coroutineContext[Job]?.cancel()
    }
}
