package gr.impatron.xr.ui

import android.util.Log
import androidx.compose.foundation.background
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
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Lightweight inline GLB viewer using a non-AR [SceneView].
 *
 * Downloads the model from [url] (PocketBase) on first composition, drops it
 * into the scene scaled to a unit cube, and slowly spins it around Y so the
 * viewer can see all sides. There are no controls — the surrounding
 * fullscreen sheet is scrollable and we don't want gesture conflicts.
 *
 * If loading fails we just show a small error label instead of crashing the
 * sheet; the surrounding sheet still works for text/image/audio/video.
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Drive a slow spin on the model so the user sees it from all angles.
    var startMs by remember(url) { mutableStateOf(0L) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = SceneView(ctx)
                view.lifecycle = lifecycleOwner.lifecycle
                // Pull camera back so a unit-cube model fits comfortably with
                // a slight downward tilt — same vibe as a turntable display.
                view.cameraNode.position = Position(x = 0f, y = 0.5f, z = 2.2f)
                view.cameraNode.lookAt(Position(0f, 0f, 0f))

                // Default main light: slightly warm key light from above-left.
                view.mainLightNode?.let { ln ->
                    ln.position = Position(x = -1f, y = 2f, z = 1f)
                    ln.lookAt(Position(0f, 0f, 0f))
                }

                // Cool fill light from the opposite side so shaded sides of PBR
                // models don't go pitch black.
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

                view.onFrame = { _ ->
                    val node = modelNode
                    if (node != null) {
                        if (startMs == 0L) startMs = System.currentTimeMillis()
                        val elapsedSec =
                            (System.currentTimeMillis() - startMs) / 1000f
                        // 25°/s — slow enough to study geometry, fast enough
                        // not to look frozen.
                        node.rotation = Rotation(
                            x = 0f,
                            y = (elapsedSec * 25f) % 360f,
                            z = 0f,
                        )
                    }
                }
                view
            },
        )

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
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
                modifier = Modifier.fillMaxSize().background(Color(0xAA0A0A0A)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "⚠",
                        color = Color(0xFFD35D4B),
                        fontSize = 32.sp,
                    )
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
    }

    // Download + load on background, then attach the ModelNode on main thread.
    LaunchedEffect(url) {
        loading = true
        error = null
        try {
            val bytes = PB.download(url)
            // Filament wants a direct buffer for glb parsing.
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
            val sv = sceneViewRef
                ?: throw IllegalStateException("SceneView δεν αρχικοποιήθηκε")
            val instance = withContext(Dispatchers.Main) {
                sv.modelLoader.createModelInstance(buffer)
            }
            val node = ModelNode(
                modelInstance = instance,
                // scaleToUnits = 1 → model fits inside a 1x1x1 cube regardless of
                // its native scale (some Blender exports are in cm, others in m).
                scaleToUnits = 1.0f,
                centerOrigin = Float3(0f, 0f, 0f),
            )
            node.isEditable = false
            modelNode = node
            sv.addChildNode(node)
            loading = false
        } catch (e: Exception) {
            Log.e("InlineModelViewer", "load failed", e)
            error = e.message ?: "άγνωστο σφάλμα"
            loading = false
        }
    }

    DisposableEffect(url) {
        onDispose {
            // SceneView's lifecycle integration handles engine cleanup; we just
            // detach the model node so the next URL gets a clean scene.
            val sv = sceneViewRef
            modelNode?.let { sv?.removeChildNode(it) }
            fillLightNode?.let { sv?.removeChildNode(it) }
            modelNode = null
            fillLightNode = null
        }
    }
}
