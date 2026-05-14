package gr.impatron.xr.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import gr.impatron.xr.ARPack
import gr.impatron.xr.ARSceneData
import gr.impatron.xr.ar.ARSceneController
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay

/**
 * AR scanner page. Owns the ARSceneView lifecycle and emits [onDetected]
 * after a short celebratory flash so the user gets visual confirmation
 * before the navigation transitions to the content page.
 */
@Composable
fun ScanPage(
    pack: ARPack,
    onBack: () -> Unit,
    onDetected: (ARSceneData) -> Unit,
) {
    var status by remember { mutableStateOf("Σκάναρε μια κάρτα") }
    var flash by remember { mutableStateOf<ARSceneData?>(null) }
    val rootView = LocalView.current

    LaunchedEffect(flash) {
        val s = flash ?: return@LaunchedEffect
        rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        delay(750)
        onDetected(s)
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        // ARSceneView is mounted ONLY while we're still hunting for a card.
        // The moment a match comes in (flash != null), we unmount it. Why:
        // ARCore's image subsystem stops as soon as the SurfaceView starts
        // animating away (page transition), but ARSceneView's FrameCallback
        // keeps firing session.update() — that's an AR_ERROR_FATAL on the
        // next frame ("subsystem Image is not started"). Unmounting here
        // routes through AndroidView.onRelease → sceneView.destroy() inside
        // the same frame, killing the source before it can crash.
        if (flash == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ARSceneView(ctx).apply {
                        val controller = ARSceneController(
                            sceneView = this,
                            context = ctx,
                            pack = pack,
                            onStatusChanged = { s -> status = s },
                            onCardDetected = { scene ->
                                if (flash == null) flash = scene
                            },
                        )
                        controller.attach()
                    }
                },
                onRelease = { sceneView ->
                    // Belt + braces: clear the frame callback first so any
                    // in-flight choreographer tick can't reach session.update,
                    // then destroy.
                    try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                    try { sceneView.session?.pause() } catch (_: Throwable) {}
                    try { sceneView.destroy() } catch (_: Throwable) {}
                },
            )

            ViewfinderVignette()
            ScanReticle()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(
                    icon = Icons.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Πίσω",
                )
                Spacer(Modifier.width(12.dp))
                StatusPill(text = status)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                HelperBubble()
            }
        } else {
            // Calm black background while the celebration plays — the AR
            // view is already gone.
            Box(modifier = Modifier.fillMaxSize().background(Palette.Bg))
        }

        // Detection celebration overlay always sits on top.
        AnimatedVisibility(
            visible = flash != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.04f),
        ) {
            flash?.let { DetectionFlash(it) }
        }
    }
}

@Composable
private fun ViewfinderVignette() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top dim band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color(0x00000000)),
                    ),
                ),
        )
        // Bottom dim band
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0x00000000), Color(0xCC000000)),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xCC0A0A0A),
        shape = CircleShape,
        modifier = Modifier
            .size(42.dp)
            .border(1.dp, Palette.Border, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Palette.OnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        color = Color(0xCC0A0A0A),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.border(1.dp, Palette.Border, RoundedCornerShape(22.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing dot — gives a "looking…" feel
            val pulse = rememberInfiniteTransition(label = "dot")
            val alpha by pulse.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dotAlpha",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Palette.Gold.copy(alpha = alpha)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = Palette.OnSurface,
                fontSize = 13.sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun HelperBubble() {
    Surface(
        color = Color(0xAA0A0A0A),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)),
    ) {
        Text(
            text = "Κράτα την κάμερα σταθερή πάνω από την κάρτα",
            color = Palette.OnSurface.copy(alpha = 0.85f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/**
 * Animated targeting reticle: an outer ring that pulses + four corner
 * brackets + a tiny center crosshair. Feels purposeful without being noisy.
 */
@Composable
private fun ScanReticle() {
    val pulse = rememberInfiniteTransition(label = "scan")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val ringScale by pulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringScale",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val w = size.width
            val h = size.height
            val len = w * 0.18f
            val strokeW = 5f
            val cap = StrokeCap.Round
            val c = Palette.Gold.copy(alpha = alpha)

            // Outer ring (faint, pulses slowly)
            drawCircle(
                color = Palette.Gold.copy(alpha = 0.25f),
                radius = (w / 2) * ringScale,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )

            // 4 corner brackets
            // TL
            drawLine(c, start = Offset(0f, len), end = Offset(0f, 0f), strokeWidth = strokeW, cap = cap)
            drawLine(c, start = Offset(0f, 0f), end = Offset(len, 0f), strokeWidth = strokeW, cap = cap)
            // TR
            drawLine(c, start = Offset(w - len, 0f), end = Offset(w, 0f), strokeWidth = strokeW, cap = cap)
            drawLine(c, start = Offset(w, 0f), end = Offset(w, len), strokeWidth = strokeW, cap = cap)
            // BR
            drawLine(c, start = Offset(w, h - len), end = Offset(w, h), strokeWidth = strokeW, cap = cap)
            drawLine(c, start = Offset(w, h), end = Offset(w - len, h), strokeWidth = strokeW, cap = cap)
            // BL
            drawLine(c, start = Offset(len, h), end = Offset(0f, h), strokeWidth = strokeW, cap = cap)
            drawLine(c, start = Offset(0f, h), end = Offset(0f, h - len), strokeWidth = strokeW, cap = cap)

            // Tiny center crosshair
            val cx = w / 2
            val cy = h / 2
            val cross = 14f
            val crossColor = Palette.Gold.copy(alpha = alpha * 0.7f)
            drawLine(crossColor, start = Offset(cx - cross, cy), end = Offset(cx + cross, cy), strokeWidth = 2f, cap = cap)
            drawLine(crossColor, start = Offset(cx, cy - cross), end = Offset(cx, cy + cross), strokeWidth = 2f, cap = cap)
        }
    }
}

@Composable
private fun DetectionFlash(scene: ARSceneData) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x55C9A86A)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF20A0A0A),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(28.dp)
                .border(1.dp, Palette.Gold.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Palette.Gold,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "ΑΝΑΓΝΩΡΙΣΤΗΚΕ",
                    color = Palette.Gold,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = scene.card.name,
                    color = Palette.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                scene.card.subtitle?.let { sub ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        color = Palette.OnSurfaceDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
