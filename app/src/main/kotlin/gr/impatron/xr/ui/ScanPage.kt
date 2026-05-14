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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
 *
 * The flash also doubles as the buffer that lets Filament's onRelease
 * tear down completely before the next page potentially spins up its own
 * SceneView (the inline 3D viewer).
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
        delay(700)
        onDetected(s)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // ARSceneView ÷ stays mounted until flash + parent navigation away.
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
                try { sceneView.destroy() } catch (_: Throwable) {}
            },
        )

        ScanBrackets()

        // Top bar with back button + status pill.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Surface(
                color = Color(0xCC0A0A0A),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = status,
                    color = Color(0xFFF5F1E8),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Help text at the bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "Κράτα την κάμερα σταθερή πάνω από την κάρτα",
                color = Color(0xFFF5F1E8).copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        // Detection flash overlay.
        AnimatedVisibility(
            visible = flash != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            flash?.let { DetectionFlash(it) }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Surface(
        color = Color(0xCC0A0A0A),
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "←",
                color = Color(0xFFF5F1E8),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ScanBrackets() {
    val pulse = rememberInfiniteTransition(label = "scan")
    val alpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val w = size.width
                val h = size.height
                val len = w * 0.20f
                val strokeW = 6f
                val cap = StrokeCap.Round
                val c = Color(0xFFC9A86A).copy(alpha = alpha)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, len),
                    end = androidx.compose.ui.geometry.Offset(0f, 0f), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(len, 0f), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w - len, 0f),
                    end = androidx.compose.ui.geometry.Offset(w, 0f), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, 0f),
                    end = androidx.compose.ui.geometry.Offset(w, len), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, h - len),
                    end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, h),
                    end = androidx.compose.ui.geometry.Offset(w - len, h), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(len, h),
                    end = androidx.compose.ui.geometry.Offset(0f, h), strokeWidth = strokeW, cap = cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, h),
                    end = androidx.compose.ui.geometry.Offset(0f, h - len), strokeWidth = strokeW, cap = cap)
            }
        }
    }
}

@Composable
private fun DetectionFlash(scene: ARSceneData) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66C9A86A)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xEE0A0A0A),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC9A86A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color(0xFF0A0A0A),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Αναγνωρίστηκε",
                    color = Color(0xFFA9A59C),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = scene.card.name,
                    color = Color(0xFFF5F1E8),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
