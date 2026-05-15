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
        // Per-event nodes registered as they mount, so the timeline tick can
        // toggle visibility without having to walk root's children every frame.
        val eventNodes: MutableMap<String, Node> = mutableMapOf(),
        var mounted: Boolean = false,
        var visible: Boolean = false,
        // Wall-clock instant the timeline started. We loop the whole card's
        // event range so audio/video/3D/text animations stay in sync without
        // needing the user to re-scan.
        var timelineStartMs: Long = 0L,
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
            // Always tick entries — even when no image was updated this
            // frame — so the timeline keeps running and anchor-following
            // smoothly tracks the world.
            tickEntries()
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
        if (img.trackingState != TrackingState.TRACKING) return

        // ONE-AT-A-TIME guard: if we're already showing content for a
        // different card, ignore the new image until the user clears the
        // current one with X. Stops the AR scene from accumulating
        // overlays when several cards happen to be in the viewfinder.
        if (entries.isNotEmpty() && !entries.containsKey(scene.card.id)) {
            return
        }

        ensureEntry(scene)
        val entry = entries[scene.card.id] ?: return

        // Live tracking: update pose every FULL_TRACKING frame so the
        // overlay rides along with the physical card if the user moves it.
        // When the image goes to LAST_KNOWN_POSE / PAUSED we stop updating
        // and the content freezes at the most recent pose — ARCore's SLAM
        // keeps the camera correctly positioned relative to that world
        // point so it still appears 'on' the card from any angle.
        if (img.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING) {
            applyPose(entry.root, img.centerPose)
        }

        if (!entry.visible) {
            entry.root.isVisible = true
            entry.visible = true
            entry.timelineStartMs = System.currentTimeMillis()
        }
        if (activeCardId != scene.card.id) {
            activeCardId = scene.card.id
            onSceneFound(scene.card.name, scene.card.subtitle)
        }
    }

    /** Per-frame tick for the timeline. Called even when no image was
     *  updated this frame so visibility / audio gating stays smooth. */
    private fun tickEntries() {
        for (entry in entries.values) tickTimeline(entry)
    }

    /**
     * Public API for the Compose layer: figure out which timeline event sits
     * under the given screen pixel. Walks the parent chain because Filament's
     * hit test often returns a child mesh of our registered ModelNode.
     *
     * Uses `collisionSystem.hitTest` (the non-deprecated API). Falls back to
     * a brute-force "nearest event in the active card" lookup so the tap
     * still has a target when hit testing misses tiny planes — which it
     * often does on phones where the user's finger covers most of the
     * overlay quad.
     */
    fun findEventAt(x: Float, y: Float): ARTimelineEvent? {
        val hits = try {
            sceneView.collisionSystem.hitTest(x, y)
        } catch (_: Throwable) {
            emptyList()
        }
        Log.i(TAG, "findEventAt($x,$y) -> ${hits.size} hits")
        for (hit in hits) {
            var node: Node? = hit.node
            while (node != null) {
                for ((_, entry) in entries) {
                    val eventId = entry.eventNodes.entries
                        .firstOrNull { it.value === node }?.key
                    if (eventId != null) {
                        Log.i(TAG, "tap matched event $eventId via hitTest")
                        return entry.scene.events.firstOrNull { it.id == eventId }
                    }
                }
                node = node.parent
            }
        }
        // Fallback: project each registered event node to screen and pick the
        // closest one within a generous radius. Filament's hit precision can
        // be flaky for thin planes; finger-size tolerance keeps taps useful.
        val active = activeCardId?.let { entries[it] } ?: entries.values.firstOrNull()
        if (active != null) {
            val tolerancePx = 220f
            var bestId: String? = null
            var bestDist = Float.MAX_VALUE
            for ((eventId, node) in active.eventNodes) {
                if (!node.isVisible) continue
                val world = node.worldPosition
                val screen = try {
                    sceneView.cameraNode.worldToScreenPoint(
                        io.github.sceneview.collision.Vector3(world.x, world.y, world.z),
                    )
                } catch (_: Throwable) { continue }
                val dx = screen.x - x
                val dy = screen.y - y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < bestDist && d < tolerancePx) {
                    bestDist = d
                    bestId = eventId
                }
            }
            if (bestId != null) {
                Log.i(TAG, "tap matched event $bestId via screen-projection (${bestDist}px)")
                return active.scene.events.firstOrNull { it.id == bestId }
            }
        }
        Log.i(TAG, "tap did not match any event")
        return null
    }

    /** Public API: user pressed X — wipe everything currently anchored. */
    fun clearAll() {
        for (id in entries.keys.toList()) destroyEntry(id)
        activeCardId = null
        onSceneLost()
    }

    /** Public API: anything anchored right now? Used by UI to show/hide X. */
    fun hasAnchors(): Boolean = entries.isNotEmpty()

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
        entry.timelineStartMs = System.currentTimeMillis()
        try {
            placeOutline(entry.root)
            for (event in entry.scene.events) {
                placeEvent(event, entry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "mountEvents failed", e)
        }
    }

    /**
     * Drive event visibility from the timeline. Each card's events have a
     * `start` and `end` in seconds; we loop over the max `end` so the
     * presentation auto-repeats while the user holds the card in view.
     */
    private fun tickTimeline(entry: CardEntry) {
        if (!entry.visible) return
        val events = entry.scene.events
        if (events.isEmpty()) return
        val maxEnd = events.maxOfOrNull { it.end }?.coerceAtLeast(0.1f) ?: return
        val elapsed = (System.currentTimeMillis() - entry.timelineStartMs) / 1000f
        val t = if (maxEnd > 0f) elapsed % maxEnd else elapsed
        for (event in events) {
            val node = entry.eventNodes[event.id] ?: continue
            val active = t in event.start..event.end
            if (node.isVisible != active) node.isVisible = active

            // Audio / video: drive playback from the same gate so we don't
            // hear sound from off-timeline events.
            if (event.kind == TimelineKind.AUDIO || event.kind == TimelineKind.VIDEO) {
                val mp = audioPlayers[event.id]
                if (mp != null) {
                    try {
                        if (active && !mp.isPlaying) mp.start()
                        else if (!active && mp.isPlaying) mp.pause()
                    } catch (_: Exception) { /* state machine errors are fine */ }
                }
            }
        }
    }

    private fun applyPose(node: Node, pose: Pose) {
        node.position = Position(pose.tx(), pose.ty(), pose.tz())
        node.quaternion = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw())
    }

    private suspend fun placeEvent(event: ARTimelineEvent, entry: CardEntry) {
        Log.i(TAG, "placing event=${event.id} kind=${event.kind} asset=${event.asset?.name}")
        try {
            val node: Node? = when (event.kind) {
                TimelineKind.MODEL -> placeModel(event, entry.root)
                TimelineKind.IMAGE -> placeImageQuad(event, entry.root)
                TimelineKind.TEXT -> placeTextQuad(event, entry.root)
                TimelineKind.AUDIO -> placeAudio(event, entry.root)
                TimelineKind.VIDEO -> placeVideoPlaceholder(event, entry.root)
                else -> null
            }
            if (node != null) {
                // Hidden until the timeline tick says otherwise — avoids a
                // single-frame pop of every event when the card is detected.
                node.isVisible = false
                entry.eventNodes[event.id] = node
            }
        } catch (e: Exception) {
            Log.e(TAG, "placeEvent failed for ${event.id}", e)
        }
    }

    private suspend fun placeModel(event: ARTimelineEvent, parent: Node): Node? {
        val url = event.asset?.fileUrl ?: return null
        Log.i(TAG, "downloading model $url")
        val cacheFile = File(context.cacheDir, "model_${event.id}.glb")
        try {
            val bytes = withContext(Dispatchers.IO) { PB.download(url) }
            withContext(Dispatchers.IO) { cacheFile.writeBytes(bytes) }
        } catch (e: Exception) {
            Log.e(TAG, "model download failed for $url", e)
            return null
        }
        val instance = runCatching {
            sceneView.modelLoader.createModelInstance(cacheFile)
        }.onFailure { Log.e(TAG, "model createModelInstance failed for ${event.id}", it) }
            .getOrNull() ?: return null
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
            // Drag/pinch/rotate gestures work out of the box once editable.
            // Tappable but not draggable — content opens its own fullscreen
            // viewer (Confirm → EventViewer) instead of moving inside AR.
            isTouchable = true
            isEditable = false
        }
        parent.addChildNode(node)
        return node
    }

    private suspend fun placeImageQuad(event: ARTimelineEvent, parent: Node): Node? {
        val url = event.asset?.fileUrl ?: return null
        val pngBytes = withContext(Dispatchers.IO) { PB.download(url) }
        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        }
        val aspect = if (bmp != null) bmp.width.toFloat() / bmp.height else 1f
        bmp?.recycle()
        val glb = withContext(Dispatchers.Default) { GltfQuad.build(pngBytes, aspect) }
        return placeQuad(glb, "image_${event.id}", event, parent)
    }

    private suspend fun placeTextQuad(event: ARTimelineEvent, parent: Node): Node? {
        val txt = event.text ?: return null
        if (txt.isBlank()) return null
        val rendered = withContext(Dispatchers.Default) { TextPng.render(txt) }
        val glb = withContext(Dispatchers.Default) {
            GltfQuad.build(rendered.bytes, rendered.aspect)
        }
        return placeQuad(glb, "text_${event.id}", event, parent)
    }

    private suspend fun placeAudio(event: ARTimelineEvent, parent: Node): Node? {
        val url = event.asset?.fileUrl ?: return null
        startMediaPlayer(event.id, url, event.loop, event.autoplay)
        val rendered = withContext(Dispatchers.Default) {
            TextPng.render("🔊  ${event.asset?.name ?: "Audio"}")
        }
        val glb = withContext(Dispatchers.Default) {
            GltfQuad.build(rendered.bytes, rendered.aspect)
        }
        return placeQuad(glb, "audio_${event.id}", event, parent)
    }

    private data class VideoRig(
        val surface: Surface,
        val surfaceTexture: SurfaceTexture,
        val stream: Stream,
        val texture: com.google.android.filament.Texture,
    )

    private val videoRigs = mutableMapOf<String, VideoRig>()

    private suspend fun placeVideoPlaceholder(event: ARTimelineEvent, parent: Node): Node? {
        val url = event.asset?.fileUrl ?: return null
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
            return plane
        } catch (e: Exception) {
            Log.e(TAG, "video plane failed for $url", e)
            return null
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
    ): Node? {
        val cacheFile = File(context.cacheDir, "$cacheName.glb")
        withContext(Dispatchers.IO) { cacheFile.writeBytes(glbBytes) }
        val instance = runCatching {
            sceneView.modelLoader.createModelInstance(cacheFile)
        }.onFailure { Log.e(TAG, "quad createModelInstance failed for $cacheName", it) }
            .getOrNull()
        if (instance == null) {
            Log.w(TAG, "quad createModelInstance null for $cacheName")
            return null
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
            // Tappable but not draggable — content opens its own fullscreen
            // viewer (Confirm → EventViewer) instead of moving inside AR.
            isTouchable = true
            isEditable = false
        }
        parent.addChildNode(node)
        return node
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
        // Stop audio + tear down per-event video rigs bound to this card.
        // Previously we only released MediaPlayer; Filament Stream /
        // SurfaceTexture / Texture leaked, and on re-scan the camera surface
        // would occasionally fight the orphaned streams.
        for (event in entry.scene.events) {
            audioPlayers.remove(event.id)?.let { mp ->
                try { if (mp.isPlaying) mp.stop(); mp.release() } catch (_: Exception) {}
            }
            videoRigs.remove(event.id)?.let { rig ->
                try { rig.surface.release() } catch (_: Throwable) {}
                try { rig.surfaceTexture.release() } catch (_: Throwable) {}
                try { sceneView.engine.destroyStream(rig.stream) } catch (_: Throwable) {}
                try { sceneView.engine.destroyTexture(rig.texture) } catch (_: Throwable) {}
            }
        }
        entry.eventNodes.clear()
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
