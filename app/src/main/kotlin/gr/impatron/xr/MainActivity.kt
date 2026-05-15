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

/** Top-level state machine. */
private sealed class Page {
    object Intro : Page()
    object Scanner : Page()
    // Video gets its own page (not a Dialog) so the AR view fully unmounts
    // — the user specifically asked for video to open 'σε άλλο view'.
    data class VideoPlayer(val event: ARTimelineEvent) : Page()
}

/** Used by AnimatedContent to decide slide direction. */
private fun pageRank(p: Page): Int = when (p) {
    is Page.Intro -> 0
    is Page.Scanner -> 1
    is Page.VideoPlayer -> 2
}

@Composable
private fun AppRoot(cameraReady: Boolean) {
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<Page>(Page.Intro) }
    // pendingEvent shows the confirm dialog; openEvent mounts the viewer.
    var pendingEvent by remember { mutableStateOf<ARTimelineEvent?>(null) }
    var openEvent by remember { mutableStateOf<ARTimelineEvent?>(null) }

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

    // Centralised back handling. Walks the most-modal-first stack:
    //   pendingEvent dialog → openEvent viewer → VideoPlayer → Scanner →
    //   Intro (where it falls through to the system which exits the app).
    // Pre-empts the system back press so it can't accidentally finish the
    // activity while ARSceneView / Filament are still mid-teardown.
    val backTarget: (() -> Unit)? = when {
        pendingEvent != null -> {
            Log.i(TAG, "back: dismiss confirm dialog"); { pendingEvent = null }
        }
        openEvent != null -> {
            Log.i(TAG, "back: close event viewer"); { openEvent = null }
        }
        page is Page.VideoPlayer -> {
            Log.i(TAG, "back: VideoPlayer → Scanner"); { page = Page.Scanner }
        }
        page is Page.Scanner -> {
            Log.i(TAG, "back: Scanner → Intro"); { page = Page.Intro }
        }
        else -> null // Intro: let system back close the app naturally
    }
    BackHandler(enabled = backTarget != null) {
        backTarget?.invoke()
    }

    // Horizontal slide between Intro / Scanner / VideoPlayer — feels like a
    // forward / back navigation stack without dragging in a full Navigation
    // library.
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
                    if (cameraReady) page = Page.Scanner
                    else error = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις."
                },
                // Tapping a card on intro goes straight to the scanner too —
                // the spec is camera-first, the list is a discovery aid.
                onPickCard = {
                    if (cameraReady) page = Page.Scanner
                    else error = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις."
                },
            )
            is Page.Scanner -> {
                if (cameraReady) {
                    ScannerPage(
                        pack = resolved,
                        onBack = { page = Page.Intro },
                        onEventTapped = { e ->
                            // Only ask if nothing is already showing — avoids
                            // accidental double-prompts on jittery taps.
                            if (pendingEvent == null && openEvent == null) {
                                pendingEvent = e
                            }
                        },
                    )
                } else {
                    ErrorBox(
                        title = "Άρνηση πρόσβασης",
                        message = "Δώσε πρόσβαση στην κάμερα από τις ρυθμίσεις.",
                    )
                }
            }
            is Page.VideoPlayer -> VideoPlayerPage(
                event = current.event,
                onBack = { page = Page.Scanner },
            )
        }
    }

    // Confirmation overlay — same level as the page switcher so the AR view
    // continues rendering underneath the scrim.
    pendingEvent?.let { event ->
        ConfirmOpenDialog(
            event = event,
            onConfirm = {
                when (event.kind) {
                    // Video gets its own page so the AR view fully unmounts
                    // and the player is true edge-to-edge.
                    TimelineKind.VIDEO -> {
                        page = Page.VideoPlayer(event)
                        pendingEvent = null
                    }
                    // Everything else opens as a lightweight Compose dialog
                    // on top of the AR view (text / image / audio / 3D model
                    // — 3D fires the Scene Viewer intent from inside the
                    // viewer).
                    else -> {
                        openEvent = event
                        pendingEvent = null
                    }
                }
            },
            onDismiss = { pendingEvent = null },
        )
    }

    // Fullscreen viewer for the confirmed event.
    openEvent?.let { event ->
        EventViewer(event = event, onClose = { openEvent = null })
    }
}

/**
 * AR scanner page. Hosts the ARSceneView and exposes per-event taps via
 * `cameraNode.hitTest` driven by Compose `detectTapGestures`. Pure visual /
 * camera plumbing — opening event viewers happens at AppRoot level.
 */
@Composable
private fun ScannerPage(
    pack: ARPack,
    onBack: () -> Unit,
    onEventTapped: (ARTimelineEvent) -> Unit,
) {
    var status by remember { mutableStateOf("Σκάναρε μια κάρτα") }
    var trackedName by remember { mutableStateOf<String?>(null) }
    var trackedSubtitle by remember { mutableStateOf<String?>(null) }
    var controllerRef by remember { mutableStateOf<ARSceneController?>(null) }
    var hasContent by remember { mutableStateOf(false) }
    val rootView = LocalView.current

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Wrap construction in try/catch — ARSceneView's constructor
                // touches camera permission state and Filament JNI, both of
                // which can fail on resume after a Scene Viewer intent.
                try {
                    ARSceneView(ctx).apply {
                        val controller = ARSceneController(
                            sceneView = this,
                            context = ctx,
                            pack = pack,
                            onStatusChanged = { s -> status = s },
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
                                // hasContent persists — cleared only by X.
                            },
                        )
                        controller.attach()
                        controllerRef = controller
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "ARSceneView factory failed", e)
                    status = "Σφάλμα κάμερας — δοκίμασε ξανά"
                    // Return a benign empty View so AndroidView doesn't NPE.
                    android.widget.FrameLayout(ctx)
                }
            },
            onRelease = { view ->
                // Bullet-proof teardown order so the next mount starts on a
                // clean Filament + ARCore state. Each step is independently
                // try/catch'd because partial failures shouldn't block the
                // next one.
                val sceneView = view as? ARSceneView
                if (sceneView != null) {
                    try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                    try { sceneView.session?.pause() } catch (_: Throwable) {}
                }
                try { controllerRef?.detach() } catch (_: Throwable) {}
                controllerRef = null
                if (sceneView != null) {
                    try { sceneView.destroy() } catch (_: Throwable) {}
                }
                Log.i(TAG, "ScannerPage AR view released")
            },
        )

        // Transparent tap-capture sibling layered ON TOP of the AR view so
        // SceneView's own gesture detector can't swallow our tap. Compose
        // doesn't see touches that the embedded SurfaceView consumes, so we
        // need our own layer here.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controllerRef) {
                    detectTapGestures(onTap = { off: Offset ->
                        val ev = controllerRef?.findEventAt(off.x, off.y)
                            ?: return@detectTapGestures
                        rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onEventTapped(ev)
                    })
                },
        )

        ViewfinderVignette(showBottom = hasContent)
        AnimatedVisibility(
            visible = !hasContent,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            ScanReticle()
        }

        // Top bar: back arrow, status pill (or tracked name), clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(10.dp))
            StatusPill(text = trackedName ?: status, tracking = trackedName != null)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = hasContent,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                ClearButton(
                    onClick = {
                        rootView.performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY,
                        )
                        controllerRef?.clearAll()
                        hasContent = false
                        trackedName = null
                        trackedSubtitle = null
                    },
                )
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
