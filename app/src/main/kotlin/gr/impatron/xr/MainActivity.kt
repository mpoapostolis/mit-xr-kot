package gr.impatron.xr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import gr.impatron.xr.ar.ARSceneController
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay

private val AppColors = darkColorScheme(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF0A0A0A),
    primary = Color(0xFFC9A86A),
)

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A),
                ) {
                    if (permissionGranted) {
                        ARScreen()
                    } else {
                        ErrorBox(
                            title = "Άρνηση πρόσβασης",
                            message = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις.",
                        )
                    }
                }
            }
        }
    }
}

/** Animation states for the overall flow. */
private enum class FlowStage {
    LOADING_PACK,         // PocketBase fetch in progress
    SCANNING,             // AR view live, looking for a card
    DETECTED_FLASH,       // brief celebratory overlay before the sheet rises
    SHEET,                // content sheet shown
}

@Composable
private fun ARScreen() {
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // The scene we're animating into / showing.
    var pendingScene by remember { mutableStateOf<ARSceneData?>(null) }
    var activeScene by remember { mutableStateOf<ARSceneData?>(null) }
    var status by remember { mutableStateOf("Φόρτωση σκηνών…") }
    var controllerRef by remember { mutableStateOf<ARSceneController?>(null) }

    // Haptic — gives the user a satisfying confirmation when the card is found.
    val rootView = LocalView.current

    // Stage is derived from the scene state.
    val stage: FlowStage = when {
        error != null || pack == null -> FlowStage.LOADING_PACK
        activeScene != null -> FlowStage.SHEET
        pendingScene != null -> FlowStage.DETECTED_FLASH
        else -> FlowStage.SCANNING
    }

    LaunchedEffect(Unit) {
        try {
            val p = PB.fetchPack()
            if (p.scenes.isEmpty()) {
                error = "Δεν υπάρχουν δημοσιευμένες κάρτες."
                return@LaunchedEffect
            }
            pack = p
            status = "Άνοιγμα κάμερας…"
        } catch (e: Exception) {
            error = e.message ?: "Σφάλμα σύνδεσης"
        }
    }

    // Detection → flash → sheet pipeline. The delay accomplishes two things:
    //   (1) the user sees a brief confirmation that the card was recognised
    //   (2) ARSceneView's onRelease has time to fully tear down Filament so
    //       the inline 3D viewer inside the sheet doesn't trip over a stale
    //       OpenGL context.
    LaunchedEffect(pendingScene) {
        val s = pendingScene ?: return@LaunchedEffect
        rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        delay(650)
        activeScene = s
        pendingScene = null
    }

    if (error != null) {
        ErrorBox(title = "Σφάλμα", message = error!!)
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // 1) Loading splash — top layer so it covers everything until pack is in.
        AnimatedVisibility(
            visible = stage == FlowStage.LOADING_PACK,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(350)),
        ) {
            Splash(status = status)
        }

        // 2) AR scanner view — mounted whenever we don't yet have an active scene.
        // We intentionally leave it mounted during DETECTED_FLASH so the user
        // still sees the camera feed underneath the celebration overlay.
        AnimatedVisibility(
            visible = stage == FlowStage.SCANNING || stage == FlowStage.DETECTED_FLASH,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            val resolved = pack
            if (resolved != null) {
                ARScanner(
                    pack = resolved,
                    status = status,
                    onStatusChange = { status = it },
                    onCardDetected = { scene ->
                        if (pendingScene == null && activeScene == null) {
                            pendingScene = scene
                        }
                    },
                    onControllerReady = { controllerRef = it },
                )
            }
        }

        // 3) Detection celebration — brief flash + card title.
        AnimatedVisibility(
            visible = stage == FlowStage.DETECTED_FLASH,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            pendingScene?.let { DetectionFlash(it) }
        }

        // 4) Content sheet — slides up from the bottom for a tactile feel.
        AnimatedVisibility(
            visible = stage == FlowStage.SHEET,
            enter = slideInVertically(
                animationSpec = tween(320),
                initialOffsetY = { full -> full },
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(260),
                targetOffsetY = { full -> full },
            ) + fadeOut(tween(180)),
        ) {
            activeScene?.let { scene ->
                gr.impatron.xr.ui.ContentSheet(
                    scene = scene,
                    onDismiss = {
                        activeScene = null
                        controllerRef?.resetDetection()
                    },
                )
            }
        }
    }
}

/** Loading splash — shown while pack is fetched + AR view is preparing. */
@Composable
private fun Splash(status: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = "Ι.Μ. ΠΑΤΡΩΝ",
            color = Color(0xFFC9A86A),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ψηφιακές Παρουσιάσεις",
            color = Color(0xFFA9A59C),
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(36.dp))
        CircularProgressIndicator(
            color = Color(0xFFC9A86A),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = status,
            color = Color(0xFFA9A59C),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ARScanner(
    pack: ARPack,
    status: String,
    onStatusChange: (String) -> Unit,
    onCardDetected: (ARSceneData) -> Unit,
    onControllerReady: (ARSceneController) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    val controller = ARSceneController(
                        sceneView = this,
                        context = ctx,
                        pack = pack,
                        onStatusChanged = onStatusChange,
                        onCardDetected = onCardDetected,
                    )
                    controller.attach()
                    onControllerReady(controller)
                }
            },
            onRelease = { sceneView ->
                try { sceneView.destroy() } catch (_: Throwable) {}
            },
        )
        ScanHint()
        // Floating status pill — top center, glassy.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(top = 14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                color = Color(0xCC0A0A0A),
                contentColor = Color(0xFFF5F1E8),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/**
 * Brief celebratory overlay shown the moment a card is recognised, before the
 * content sheet animates in. Gives the user instant visual + haptic feedback
 * so the transition feels intentional rather than abrupt.
 */
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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC9A86A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color(0xFF0A0A0A),
                        fontSize = 28.sp,
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

@Composable
private fun ScanHint() {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val w = size.width
                val h = size.height
                val len = w * 0.22f
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
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Στρέψε την κάμερα\nσε μια κάρτα",
                color = Color(0xFFF5F1E8).copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorBox(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠",
                color = Color(0xFFD35D4B),
                fontSize = 48.sp,
            )
            Text(
                text = title,
                color = Color(0xFFF5F1E8),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = message,
                color = Color(0xFFA9A59C),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
