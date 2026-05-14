package gr.impatron.xr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import gr.impatron.xr.ar.ARSceneController
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@Composable
private fun ARScreen() {
    var status by remember { mutableStateOf("Φόρτωση σκηνών…") }
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeScene by remember { mutableStateOf<gr.impatron.xr.ARSceneData?>(null) }

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

    if (error != null) {
        ErrorBox(title = "Σφάλμα", message = error!!)
        return
    }

    val resolved = pack
    if (resolved == null) {
        Loading(status)
        return
    }

    var controllerRef by remember { mutableStateOf<ARSceneController?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    val controller = ARSceneController(
                        sceneView = this,
                        context = ctx,
                        pack = resolved,
                        onStatusChanged = { s -> status = s },
                        onCardDetected = { scene -> activeScene = scene },
                    )
                    controller.attach()
                    controllerRef = controller
                }
            },
        )

        // Status pill (top) while not viewing content
        if (activeScene == null) {
            ScanHint()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    color = Color(0xCC0A0A0A),
                    contentColor = Color(0xFFF5F1E8),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Fullscreen content sheet — appears on card detection
        activeScene?.let { scene ->
            gr.impatron.xr.ui.ContentSheet(
                scene = scene,
                onDismiss = {
                    activeScene = null
                    controllerRef?.onDismiss()
                },
            )
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
                val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
                val c = Color(0xFFC9A86A).copy(alpha = alpha)
                // 4 corner brackets
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, len),
                    end = androidx.compose.ui.geometry.Offset(0f, 0f), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(len, 0f), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w - len, 0f),
                    end = androidx.compose.ui.geometry.Offset(w, 0f), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, 0f),
                    end = androidx.compose.ui.geometry.Offset(w, len), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, h - len),
                    end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(w, h),
                    end = androidx.compose.ui.geometry.Offset(w - len, h), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(len, h),
                    end = androidx.compose.ui.geometry.Offset(0f, h), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, h),
                    end = androidx.compose.ui.geometry.Offset(0f, h - len), strokeWidth = stroke.width, cap = stroke.cap)
            }
            Text(
                text = "Στρέψε την κάμερα\nσε μια κάρτα",
                color = Color(0xFFF5F1E8).copy(alpha = 0.85f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Loading(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFC9A86A))
            Text(
                text = text,
                color = Color(0xFFA9A59C),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
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
