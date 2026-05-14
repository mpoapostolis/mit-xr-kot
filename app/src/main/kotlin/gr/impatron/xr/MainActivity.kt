package gr.impatron.xr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import gr.impatron.xr.ui.ContentPage
import gr.impatron.xr.ui.IntroPage
import gr.impatron.xr.ui.ScanPage

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
                    AppRoot(cameraReady = permissionGranted)
                }
            }
        }
    }
}

/**
 * Top-level state machine. Sealed types beat a string router here: the
 * compiler enforces that every page knows how to navigate.
 */
private sealed class Page {
    object Intro : Page()
    object Scan : Page()
    data class Content(val scene: ARSceneData) : Page()
}

@Composable
private fun AppRoot(cameraReady: Boolean) {
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<Page>(Page.Intro) }

    LaunchedEffect(Unit) {
        try {
            val p = PB.fetchPack()
            if (p.scenes.isEmpty()) {
                error = "Δεν υπάρχουν δημοσιευμένες κάρτες."
                return@LaunchedEffect
            }
            pack = p
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
        Splash()
        return
    }

    // Horizontal-slide transition between Intro and Scan/Content gives the
    // sense of a "forward / back" navigation stack without pulling in a full
    // Navigation library for what is genuinely three screens.
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = when {
                initialState is Page.Intro -> true
                targetState is Page.Intro -> false
                initialState is Page.Scan && targetState is Page.Content -> true
                else -> false
            }
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> direction * full } +
                fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { full -> -direction * full } +
                    fadeOut(tween(220)))
        },
        label = "page",
    ) { current ->
        when (current) {
            is Page.Intro -> IntroPage(
                scenes = resolved.scenes,
                onScan = {
                    if (cameraReady) page = Page.Scan
                    else error = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις."
                },
                onPickCard = { page = Page.Content(it) },
            )
            is Page.Scan -> {
                if (cameraReady) {
                    ScanPage(
                        pack = resolved,
                        onBack = { page = Page.Intro },
                        onDetected = { scene -> page = Page.Content(scene) },
                    )
                } else {
                    ErrorBox(
                        title = "Άρνηση πρόσβασης",
                        message = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις.",
                    )
                }
            }
            is Page.Content -> ContentPage(
                scene = current.scene,
                onBack = { page = Page.Intro },
            )
        }
    }
}

@Composable
private fun Splash() {
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
