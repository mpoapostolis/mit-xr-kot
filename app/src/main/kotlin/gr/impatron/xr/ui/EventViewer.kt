package gr.impatron.xr.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import gr.impatron.xr.ARTimelineEvent
import gr.impatron.xr.TimelineKind

/**
 * Confirmation dialog asking the user whether they want to open a tapped
 * event in fullscreen. The spec quote calls out that the AR layer should
 * "draw less attention from the exhibit" — this is the bridge between the
 * subtle on-card preview and a focused viewer experience.
 */
@Composable
fun ConfirmOpenDialog(
    event: ARTimelineEvent,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, body, cta) = remember(event.id) { dialogCopyFor(event) }
    val heroIcon = remember(event.id) { heroIconFor(event) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            color = Color(0xF20F0F0F),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Palette.Gold.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hero icon — large, kind-specific. Doubles as a quick
                // visual cue so the user knows what content they're about
                // to open even before reading the title.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Palette.GoldSoft)
                        .border(1.dp, Palette.Gold.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = heroIcon,
                        contentDescription = null,
                        tint = Palette.Gold,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    color = Palette.Gold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    color = Palette.OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Palette.Surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss),
                    ) {
                        Text(
                            text = "Άκυρο",
                            color = Palette.OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Surface(
                        color = Palette.Gold,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onConfirm),
                    ) {
                        Text(
                            text = cta,
                            color = Color(0xFF0A0A0A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun heroIconFor(event: ARTimelineEvent): ImageVector = when (event.kind) {
    TimelineKind.TEXT -> Icons.Outlined.Description
    TimelineKind.IMAGE -> Icons.Filled.Image
    TimelineKind.AUDIO -> Icons.Filled.AudioFile
    TimelineKind.VIDEO -> Icons.Filled.PlayCircleFilled
    TimelineKind.MODEL -> Icons.Filled.ViewInAr
    else -> Icons.Outlined.Description
}

private fun dialogCopyFor(event: ARTimelineEvent): Triple<String, String, String> = when (event.kind) {
    TimelineKind.TEXT -> Triple("ΚΕΙΜΕΝΟ", "Να δεις την πλήρη πληροφορία;", "Ανάγνωση")
    TimelineKind.IMAGE -> Triple("ΕΙΚΟΝΑ", "Να ανοίξεις την εικόνα σε πλήρη οθόνη;", "Άνοιγμα")
    TimelineKind.AUDIO -> Triple(
        "ΗΧΟΣ",
        "Θες να ακούσεις «${event.asset?.name ?: "Ήχος"}»;",
        "Ακρόαση",
    )
    TimelineKind.VIDEO -> Triple(
        "ΒΙΝΤΕΟ",
        "Θες να δεις το βίντεο;",
        "Αναπαραγωγή",
    )
    TimelineKind.MODEL -> Triple(
        "3D ΜΟΝΤΕΛΟ",
        "Θες να εξερευνήσεις το 3D μοντέλο;",
        "Άνοιγμα σε 3D",
    )
    else -> Triple("ΥΛΙΚΟ", "Άνοιγμα σε πλήρη οθόνη;", "Άνοιγμα")
}

/**
 * Dispatches to the right viewer based on the event kind. Two kinds open
 * outside this dialog stack:
 *   - VIDEO → MainActivity routes to Page.VideoPlayer (a full Page so the
 *     AR view fully unmounts and the player gets edge-to-edge bleed).
 *   - MODEL → Google Scene Viewer intent (separate activity, AR-capable).
 *
 * The rest (text / image / audio) are lightweight enough to live as
 * fullscreen Compose dialogs on top of the AR scene.
 */
@Composable
fun EventViewer(event: ARTimelineEvent, onClose: () -> Unit) {
    val context = LocalContext.current
    when (event.kind) {
        TimelineKind.TEXT -> TextViewer(event = event, onClose = onClose)
        TimelineKind.IMAGE -> ImageViewer(event = event, onClose = onClose)
        TimelineKind.AUDIO -> AudioViewer(event = event, onClose = onClose)
        TimelineKind.VIDEO -> {
            // Should never land here in practice — MainActivity routes VIDEO
            // to Page.VideoPlayer before EventViewer is mounted. Closing
            // immediately is a safety net.
            LaunchedEffect(event.id) { onClose() }
        }
        TimelineKind.MODEL -> {
            // Fire-and-forget Scene Viewer launch, then close ourselves so
            // the user lands back on the scanner when they press Back.
            LaunchedEffect(event.id) {
                val url = event.asset?.fileUrl
                if (url.isNullOrEmpty()) {
                    Toast.makeText(context, "Δεν υπάρχει 3D αρχείο.", Toast.LENGTH_SHORT).show()
                    onClose()
                    return@LaunchedEffect
                }
                try {
                    val uri = Uri.parse("https://arvr.google.com/scene-viewer/1.0")
                        .buildUpon()
                        .appendQueryParameter("file", url)
                        .appendQueryParameter("mode", "ar_preferred")
                        .appendQueryParameter("title", event.asset.name)
                        .build()
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.googlequicksearchbox")
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    try {
                        val uri = Uri.parse("https://arvr.google.com/scene-viewer/1.0")
                            .buildUpon()
                            .appendQueryParameter("file", url)
                            .appendQueryParameter("mode", "3d_only")
                            .build()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Throwable) {
                        Toast.makeText(
                            context,
                            "Δεν βρέθηκε Scene Viewer.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                onClose()
            }
        }
        else -> onClose()
    }
}

@Composable
private fun ViewerScaffold(
    eyebrow: String,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20A0A0A)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Palette.Surface,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, Palette.Border, CircleShape)
                            .clickable(onClick = onClose),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Κλείσιμο",
                                tint = Palette.OnSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eyebrow,
                            color = Palette.Gold,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = title,
                            color = Palette.OnSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

@Composable
private fun TextViewer(event: ARTimelineEvent, onClose: () -> Unit) {
    ViewerScaffold(eyebrow = "ΚΕΙΜΕΝΟ", title = "Πληροφορία", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Pulled-quote-style accent above the text.
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Palette.Gold),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = event.text.orEmpty(),
                color = Palette.OnSurface,
                fontSize = 18.sp,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ImageViewer(event: ARTimelineEvent, onClose: () -> Unit) {
    val url = event.asset?.fileUrl
    ViewerScaffold(
        eyebrow = "ΕΙΚΟΝΑ",
        title = event.asset?.name ?: "Εικόνα",
        onClose = onClose,
    ) {
        // Pinch-to-zoom + drag-to-pan + double-tap-to-zoom. Museum-grade
        // close-up interaction without a third-party library.
        var scale by remember(event.id) { mutableStateOf(1f) }
        var offsetX by remember(event.id) { mutableStateOf(0f) }
        var offsetY by remember(event.id) { mutableStateOf(0f) }
        val minScale = 1f
        val maxScale = 5f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .pointerInput(event.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        // Pan freely while zoomed; snap back to centre at 1×.
                        offsetX = if (newScale > 1f) offsetX + pan.x else 0f
                        offsetY = if (newScale > 1f) offsetY + pan.y else 0f
                        scale = newScale
                    }
                }
                .pointerInput(event.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            // Double-tap toggles between 1× and 2.5× — a
                            // familiar gesture from native photo viewers.
                            if (scale > 1.1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!url.isNullOrEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = event.asset?.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
            }
            // Subtle hint at the bottom, only when not zoomed.
            if (scale <= 1.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "Pinch ή διπλό άγγιγμα για μεγέθυνση",
                        color = Palette.OnSurfaceDim.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioViewer(event: ARTimelineEvent, onClose: () -> Unit) {
    val url = event.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var playing by remember(url) { mutableStateOf(false) }
    val mp = remember(url) {
        MediaPlayer().apply {
            setDataSource(url)
            isLooping = event.loop
            setOnPreparedListener {
                start()
                playing = true
            }
            setOnCompletionListener { playing = false }
            try { prepareAsync() } catch (_: Throwable) {}
        }
    }
    DisposableEffect(mp) {
        onDispose {
            try { mp.stop(); mp.release() } catch (_: Throwable) {}
        }
    }
    ViewerScaffold(
        eyebrow = "ΗΧΟΣ",
        title = event.asset?.name ?: "Ήχος",
        onClose = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Big animated waveform
            Waveform(playing = playing)
            Spacer(Modifier.height(32.dp))
            Surface(
                color = Palette.GoldSoft,
                shape = CircleShape,
                modifier = Modifier
                    .size(84.dp)
                    .border(1.5.dp, Palette.Gold, CircleShape)
                    .clickable {
                        try {
                            if (mp.isPlaying) { mp.pause(); playing = false }
                            else { mp.start(); playing = true }
                        } catch (_: Throwable) {}
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Παύση" else "Αναπαραγωγή",
                        tint = Palette.Gold,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (playing) "ΑΝΑΠΑΡΑΓΩΓΗ" else "ΠΑΥΣΗ",
                color = Palette.OnSurfaceDim,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun Waveform(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "wf")
    val heights = listOf(
        12f, 22f, 18f, 30f, 16f, 26f, 14f, 24f, 20f, 32f, 18f,
        14f, 28f, 22f, 36f, 18f, 30f, 20f, 26f, 16f, 28f, 22f,
        18f, 26f, 14f, 30f, 18f, 24f, 16f, 20f,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp),
    ) {
        for ((idx, base) in heights.withIndex()) {
            val anim by transition.animateFloat(
                initialValue = base,
                targetValue = if (playing) base * 1.6f else base,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480 + (idx * 19) % 420),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "wfBar$idx",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(anim.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        if (playing) Palette.Gold.copy(alpha = 0.85f)
                        else Palette.OnSurfaceDim.copy(alpha = 0.4f),
                    ),
            )
            Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable
private fun VideoViewer(event: ARTimelineEvent, onClose: () -> Unit) {
    val url = event.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (event.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    ViewerScaffold(
        eyebrow = "ΒΙΝΤΕΟ",
        title = event.asset?.name ?: "Βίντεο",
        onClose = onClose,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
