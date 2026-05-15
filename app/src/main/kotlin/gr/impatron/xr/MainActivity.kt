package gr.impatron.xr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import gr.impatron.xr.TimelineKind
import gr.impatron.xr.ar.ARSceneController
import gr.impatron.xr.ui.CardContentPage
import gr.impatron.xr.ui.ConfirmOpenDialog
import gr.impatron.xr.ui.EventViewer
import gr.impatron.xr.ui.IntroPage
import gr.impatron.xr.ui.Palette
import gr.impatron.xr.ui.VideoPlayerPage
import io.github.sceneview.ar.ARSceneView

private const val TAG = "XR-Main"

private val AppColors = darkColorScheme(
    background = Palette.Bg,
    surface = Palette.Bg,
    primary = Palette.Gold,
)

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: lets the camera preview + AR scene paint behind the
        // status bar / nav bar so chrome doesn't get a black strip. Compose
        // composables handle their own insets via systemBarsPadding().
        enableEdgeToEdge()
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
                    color = Palette.Bg,
                ) {
                    AppRoot(cameraReady = permissionGranted)
                }
            }
        }
    }
}

/**
 * Top-level state machine. We deliberately don't have a VideoPlayer page
 * any more: navigating away from Scanner triggered an AR view destroy /
 * recreate cycle that occasionally crashed ARCore native (MediaPipe
 * calculator graph SIGSEGV on the second pause/resume round). Video now
 * opens as a fullscreen overlay so the AR scene stays mounted and the
 * session never needs to bounce.
 */
private sealed class Page {
    object Intro : Page()
    object Scanner : Page()
    // Browse a card's events without scanning the physical card. Same
    // tap → confirm → viewer flow as the AR scanner.
    data class CardContent(val scene: ARSceneData) : Page()
}

/** Used by AnimatedContent to decide slide direction. */
private fun pageRank(p: Page): Int = when (p) {
    is Page.Intro -> 0
    is Page.Scanner -> 1
    is Page.CardContent -> 2
}

@Composable
private fun AppRoot(cameraReady: Boolean) {
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<Page>(Page.Intro) }
    // pendingEvent shows the confirm dialog; openEvent mounts the viewer.
    var pendingEvent by remember { mutableStateOf<ARTimelineEvent?>(null) }
    var openEvent by remember { mutableStateOf<ARTimelineEvent?>(null) }

    // Scanner state lifted to AppRoot so the AR view can stay mounted
    // even when the user is on Intro / CardContent. ARCore native crashes
    // (MediaPipe SIGSEGV) on the destroy + recreate cycle, so we never
    // tear it down — we just pause the session.
    var controllerRef by remember { mutableStateOf<ARSceneController?>(null) }
    var trackedName by remember { mutableStateOf<String?>(null) }
    var trackedSubtitle by remember { mutableStateOf<String?>(null) }
    var hasContent by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("Σκάναρε μια κάρτα") }
    val rootView = LocalView.current

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
        Splash("Φόρτωση…")
        return
    }

    // Back behaviour: anywhere off-Intro lands on Intro. X and back are
    // the same gesture from the user's perspective — both leave the
    // current view and return to the menu.
    val backTarget: (() -> Unit)? = when {
        pendingEvent != null -> {
            Log.i(TAG, "back: dismiss confirm dialog"); { pendingEvent = null }
        }
        openEvent != null -> {
            Log.i(TAG, "back: close event viewer"); { openEvent = null }
        }
        page is Page.CardContent -> {
            Log.i(TAG, "back: CardContent → Intro"); { page = Page.Intro }
        }
        page is Page.Scanner -> {
            Log.i(TAG, "back: Scanner → Intro"); { page = Page.Intro }
        }
        else -> null // Intro: let system back close the app naturally
    }
    BackHandler(enabled = backTarget != null) {
        backTarget?.invoke()
    }

    // Pause/resume ARCore tied to whether the Scanner page is showing.
    // Lifecycle of the ARSceneView itself is the activity's (it never
    // destroys until the app does), but pausing the session stops the
    // camera + frame callbacks while the user is on Intro / CardContent.
    LaunchedEffect(page, controllerRef) {
        val c = controllerRef ?: return@LaunchedEffect
        if (page is Page.Scanner) c.resumeSession() else c.pauseSession()
    }

    Box(Modifier.fillMaxSize()) {
        // ---- AR view + tap layer: ALWAYS mounted at this level ---
        if (cameraReady) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    try {
                        ARSceneView(ctx).apply {
                            val controller = ARSceneController(
                                sceneView = this,
                                context = ctx,
                                pack = resolved,
                                onStatusChanged = { s -> scanStatus = s },
                                onSceneFound = { name, sub ->
                                    trackedName = name
                                    trackedSubtitle = sub
                                    hasContent = true
                                    rootView.performHapticFeedback(
                                        HapticFeedbackConstants.LONG_PRESS,
                                    )
                                },
                                onSceneLost = {
                                    trackedName = null
                                    trackedSubtitle = null
                                },
                            )
                            controller.attach()
                            controllerRef = controller
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "ARSceneView factory failed", e)
                        scanStatus = "Σφάλμα κάμερας — δοκίμασε ξανά"
                        android.widget.FrameLayout(ctx)
                    }
                },
                onRelease = { view ->
                    // Only fires on activity destroy thanks to AR being
                    // lifted out of the page switcher.
                    val sceneView = view as? ARSceneView
                    try { controllerRef?.detach() } catch (_: Throwable) {}
                    controllerRef = null
                    try { sceneView?.destroy() } catch (_: Throwable) {}
                    Log.i(TAG, "AppRoot AR view released (activity teardown)")
                },
            )
            // Tap layer ON TOP of AR (active only on Scanner page so it
            // doesn't intercept taps on Intro / CardContent).
            if (page is Page.Scanner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(controllerRef) {
                            detectTapGestures(onTap = { off: Offset ->
                                val ev = controllerRef?.findEventAt(off.x, off.y)
                                    ?: return@detectTapGestures
                                rootView.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS,
                                )
                                if (pendingEvent == null && openEvent == null) {
                                    pendingEvent = ev
                                }
                            })
                        },
                )
            }
        }

        // ---- Page content layer ----
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forwardRank = pageRank(targetState) > pageRank(initialState)
                val dir = if (forwardRank) 1 else -1
                (slideInHorizontally(tween(300)) { full -> dir * full } +
                    fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { full -> -dir * full } +
                        fadeOut(tween(220)))
            },
            label = "page",
        ) { current ->
            when (current) {
                is Page.Intro -> IntroPage(
                    scenes = resolved.scenes,
                    onScan = {
                        if (cameraReady) {
                            // 'Σκάναρε κάρτα' is the explicit reset point:
                            // we forget any X-dismissals from the previous
                            // visit so the user starts a fresh scan.
                            controllerRef?.resetDismissals()
                            page = Page.Scanner
                        } else {
                            error = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις."
                        }
                    },
                    onPickCard = { scene -> page = Page.CardContent(scene) },
                )
                is Page.Scanner -> {
                    if (cameraReady) {
                        ScannerOverlay(
                            status = scanStatus,
                            trackedName = trackedName,
                            trackedSubtitle = trackedSubtitle,
                            hasContent = hasContent,
                            onBackToMenu = {
                                // Same action as back gesture — clear AR
                                // content AND return to Intro.
                                rootView.performHapticFeedback(
                                    HapticFeedbackConstants.VIRTUAL_KEY,
                                )
                                controllerRef?.clearAll()
                                hasContent = false
                                trackedName = null
                                trackedSubtitle = null
                                page = Page.Intro
                            },
                        )
                    } else {
                        ErrorBox(
                            title = "Άρνηση πρόσβασης",
                            message = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις.",
                        )
                    }
                }
                is Page.CardContent -> CardContentPage(
                    scene = current.scene,
                    onBack = { page = Page.Intro },
                    onEventTapped = { e ->
                        if (pendingEvent == null && openEvent == null) {
                            pendingEvent = e
                        }
                    },
                )
            }
        }

        // ---- Confirmation overlay ----
        pendingEvent?.let { event ->
            ConfirmOpenDialog(
                event = event,
                onConfirm = {
                    openEvent = event
                    pendingEvent = null
                },
                onDismiss = { pendingEvent = null },
            )
        }

        // ---- Fullscreen viewer for the confirmed event ----
        // Video uses the dedicated VideoPlayerPage as a Box overlay so the
        // AR view underneath isn't destroyed — that path SIGSEGVs in
        // ARCore native. Other kinds open via EventViewer.
        openEvent?.let { event ->
            if (event.kind == TimelineKind.VIDEO) {
                VideoPlayerPage(event = event, onBack = { openEvent = null })
            } else {
                EventViewer(event = event, onClose = { openEvent = null })
            }
        }
    } // end outer Box
}

/**
 * Scanner UI overlay. Chrome only — the actual AR camera view + tap
 * hit-testing live at AppRoot level so they stay mounted across page
 * navigations and don't tear down ARCore (which native-crashes on the
 * pause/destroy cycle).
 *
 * From the user's perspective: status pill, scan brackets, back/clear
 * buttons, helper bubble and scene-info card are all here. The AR
 * camera and event taps come from the layer underneath.
 */
@Composable
private fun ScannerOverlay(
    status: String,
    trackedName: String?,
    trackedSubtitle: String?,
    hasContent: Boolean,
    onBackToMenu: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ViewfinderVignette(showBottom = hasContent)
        AnimatedVisibility(
            visible = !hasContent,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            ScanReticle()
        }

        // Top bar: only a back button (Back == X == 'πάει στο menu').
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBackToMenu)
            Spacer(Modifier.width(10.dp))
            StatusPill(text = trackedName ?: status, tracking = trackedName != null)
            Spacer(Modifier.weight(1f))
            // The X is the same gesture as Back — keeps the menu only one
            // tap away whether the user reaches for the corner or the
            // top-left arrow.
            AnimatedVisibility(
                visible = hasContent,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                ClearButton(onClick = onBackToMenu)
            }
        }

        AnimatedVisibility(
            visible = !hasContent,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 36.dp),
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(160)),
        ) {
            Box(contentAlignment = Alignment.BottomCenter) { HelperBubble() }
        }

        AnimatedVisibility(
            visible = trackedName != null,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            enter = slideInVertically(tween(280)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                SceneCard(
                    name = trackedName ?: "",
                    subtitle = trackedSubtitle,
                )
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Πίσω",
                tint = Palette.OnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, tracking: Boolean) {
    Surface(
        color = if (tracking) Palette.Gold else Color(0xCC0A0A0A),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.border(
            1.dp,
            if (tracking) Palette.Gold else Palette.Border,
            RoundedCornerShape(22.dp),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pulse = rememberInfiniteTransition(label = "dot")
            val alpha by pulse.animateFloat(
                initialValue = 0.4f,
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
                    .background(
                        if (tracking) Color(0xFF0A0A0A)
                        else Palette.Gold.copy(alpha = alpha),
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = if (tracking) Color(0xFF0A0A0A) else Palette.OnSurface,
                fontSize = 13.sp,
                fontWeight = if (tracking) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    Surface(
        color = Color(0xEE0A0A0A),
        shape = CircleShape,
        modifier = Modifier
            .size(46.dp)
            .border(1.5.dp, Palette.Gold.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Καθάρισμα",
                tint = Palette.Gold,
                modifier = Modifier.size(20.dp),
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
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCameraFront,
                contentDescription = null,
                tint = Palette.Gold,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Στρέψε την κάμερα σε μια κάρτα",
                color = Palette.OnSurface.copy(alpha = 0.9f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SceneCard(name: String, subtitle: String?) {
    Surface(
        color = Color(0xF20A0A0A),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "ΕΚΘΕΜΑ · ΑΓΓΙΞΕ ΤΟ ΥΛΙΚΟ",
                color = Palette.Gold,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                color = Palette.OnSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Palette.OnSurfaceDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ViewfinderVignette(showBottom: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color(0x00000000)),
                    ),
                ),
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showBottom) 220.dp else 160.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x00000000), Color(0xCC000000)),
                    ),
                ),
        )
    }
}

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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val w = size.width
            val h = size.height
            val len = w * 0.18f
            val strokeW = 5f
            val cap = StrokeCap.Round
            val c = Palette.Gold.copy(alpha = alpha)
            drawCircle(
                color = Palette.Gold.copy(alpha = 0.22f),
                radius = (w / 2) * ringScale,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
            drawLine(c, Offset(0f, len), Offset(0f, 0f), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(0f, 0f), Offset(len, 0f), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(w - len, 0f), Offset(w, 0f), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(w, 0f), Offset(w, len), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(w, h - len), Offset(w, h), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(w, h), Offset(w - len, h), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(len, h), Offset(0f, h), strokeWidth = strokeW, cap = cap)
            drawLine(c, Offset(0f, h), Offset(0f, h - len), strokeWidth = strokeW, cap = cap)
            val cx = w / 2
            val cy = h / 2
            val cross = 14f
            val crossC = Palette.Gold.copy(alpha = alpha * 0.7f)
            drawLine(crossC, Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 2f, cap = cap)
            drawLine(crossC, Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 2f, cap = cap)
        }
    }
}

@Composable
private fun Splash(status: String) {
    // Fade + lift the brand mark in on first composition. Without it the
    // splash feels like a static loading screen; with it the app feels
    // like it's "opening".
    val animSpec = remember { tween<Float>(durationMillis = 600) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = animSpec,
        label = "splashAlpha",
    )
    val lift by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 12f,
        animationSpec = animSpec,
        label = "splashLift",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                this.translationY = lift
            },
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Palette.GoldSoft)
                    .border(1.dp, Palette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ι",
                    color = Palette.Gold,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Ι.Μ. ΠΑΤΡΩΝ",
                color = Palette.Gold,
                fontSize = 14.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ψηφιακές Παρουσιάσεις",
                color = Palette.OnSurfaceDim,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(44.dp))
        CircularProgressIndicator(
            color = Palette.Gold,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(text = status, color = Palette.OnSurfaceDim, fontSize = 11.sp)
    }
}

@Composable
private fun ErrorBox(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Soft red halo with the warning glyph centred — more dignified
            // than a single oversized ⚠.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Palette.Danger.copy(alpha = 0.18f))
                    .border(1.dp, Palette.Danger.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⚠",
                    color = Palette.Danger,
                    fontSize = 32.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                color = Palette.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Palette.OnSurfaceDim,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
