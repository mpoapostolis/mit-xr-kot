package gr.impatron.xr.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import gr.impatron.xr.PB
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Inline GLB viewer with manual gesture controls.
 *
 * Drag → rotate the model on the screen-aligned Y/X axes.
 * Pinch → uniform scale (zoom).
 *
 * Defensive against:
 *   - SceneView factory not run yet when LaunchedEffect resumes
 *   - Filament refusing the GLB (unsupported extension, corrupt asset)
 *   - The user navigating away mid-load (DisposableEffect drops references)
 */
@Composable
fun InlineModelViewer(
    name: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var sceneViewRef by remember(url) { mutableStateOf<SceneView?>(null) }
    var modelNode by remember(url) { mutableStateOf<ModelNode?>(null) }
    var fillLightNode by remember(url) { mutableStateOf<LightNode?>(null) }
    val rotation = remember(url) { floatArrayOf(0f, 0f, 0f) }
    val scale = remember(url) { floatArrayOf(1f, 1f, 1f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = modifier
            .pointerInput(url) {
                detectDragGestures { _, drag ->
                    val node = modelNode ?: return@detectDragGestures
                    rotation[1] = (rotation[1] + drag.x * 0.5f) % 360f
                    rotation[0] = (rotation[0] - drag.y * 0.5f).coerceIn(-89f, 89f)
                    try {
                        node.rotation = Rotation(rotation[0], rotation[1], rotation[2])
                    } catch (_: Throwable) { /* node destroyed mid-gesture */ }
                }
            }
            .pointerInput(url) {
                detectTransformGestures { _, _, zoom, _ ->
                    val node = modelNode ?: return@detectTransformGestures
                    val s = (scale[0] * zoom).coerceIn(0.25f, 4f)
                    scale[0] = s; scale[1] = s; scale[2] = s
                    try {
                        node.scale = Scale(s, s, s)
                    } catch (_: Throwable) { /* node destroyed mid-gesture */ }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = SceneView(ctx)
                view.lifecycle = lifecycleOwner.lifecycle
                view.cameraNode.position = Position(x = 0f, y = 0.5f, z = 2.4f)
                view.cameraNode.lookAt(Position(0f, 0f, 0f))
                view.mainLightNode?.let { ln ->
                    ln.position = Position(x = -1f, y = 2f, z = 1f)
                    ln.lookAt(Position(0f, 0f, 0f))
                }
                val fill = LightNode(
                    engine = view.engine,
                    type = LightManager.Type.DIRECTIONAL,
                ) {
                    color(0.75f, 0.82f, 1.0f)
                    intensity(40_000f)
                    direction(0.6f, -0.5f, 0.4f)
                    castShadows(false)
                }
                view.addChildNode(fill)
                fillLightNode = fill
                sceneViewRef = view
                view
            },
            onRelease = { sv ->
                try { sv.destroy() } catch (_: Throwable) {}
            },
        )

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFC9A86A),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Φόρτωση μοντέλου…",
                        color = Color(0xFFF5F1E8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        error?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA0A0A0A)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(text = "⚠", color = Color(0xFFD35D4B), fontSize = 32.sp)
                    Text(
                        text = "Αδυναμία φόρτωσης 3D",
                        color = Color(0xFFF5F1E8),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = msg,
                        color = Color(0xFFA9A59C),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (!loading && error == null && modelNode != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = "Σύρε για περιστροφή · Pinch για zoom",
                    color = Color(0xFFA9A59C).copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }

    LaunchedEffect(url) {
        loading = true
        error = null
        try {
            // 1) Pull bytes off the network.
            val bytes = PB.download(url)
            if (bytes.isEmpty()) throw IllegalStateException("Κενό αρχείο")

            // 2) Wait for the SceneView factory to fire. In practice it runs
            //    during the first layout pass; the suspension on PB.download
            //    already gives it plenty of time, but be defensive in case the
            //    sheet is animating in and layout is deferred.
            var attempts = 0
            while (sceneViewRef == null && attempts < 100) {
                delay(20)
                attempts++
            }
            val sv = sceneViewRef
                ?: throw IllegalStateException("SceneView δεν ξεκίνησε")

            // 3) Filament wants a direct buffer for glTF parsing.
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes); rewind()
            }

            // 4) Parse on the render thread — anything else risks UAF in the
            //    gltfio AssetLoader.
            val instance = withContext(Dispatchers.Main) {
                try {
                    sv.modelLoader.createModelInstance(buffer)
                } catch (e: Throwable) {
                    Log.e("InlineModelViewer", "Filament parse failed", e)
                    null
                }
            } ?: throw IllegalStateException(
                "Το αρχείο δεν είναι έγκυρο GLB ή χρησιμοποιεί επεκτάσεις που " +
                    "δεν υποστηρίζονται από το Filament.",
            )

            // 5) Build the node and attach.
            val node = ModelNode(
                modelInstance = instance,
                scaleToUnits = 1.0f,
                centerOrigin = Float3(0f, 0f, 0f),
            )
            node.isEditable = false
            modelNode = node
            try {
                sv.addChildNode(node)
            } catch (e: Throwable) {
                Log.e("InlineModelViewer", "addChildNode failed", e)
                throw IllegalStateException("Αδυναμία ένταξης στη σκηνή")
            }
            loading = false
        } catch (e: Throwable) {
            Log.e("InlineModelViewer", "load failed for $url", e)
            error = e.message ?: "άγνωστο σφάλμα"
            loading = false
        }
    }

    DisposableEffect(url) {
        onDispose {
            val sv = sceneViewRef
            try { modelNode?.let { sv?.removeChildNode(it) } } catch (_: Throwable) {}
            try { fillLightNode?.let { sv?.removeChildNode(it) } } catch (_: Throwable) {}
            modelNode = null
            fillLightNode = null
        }
    }
}
