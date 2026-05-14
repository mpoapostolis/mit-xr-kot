package gr.impatron.xr.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import gr.impatron.xr.ARSceneData
import gr.impatron.xr.ARTimelineEvent
import gr.impatron.xr.TimelineKind

/**
 * Logical buckets the bottom navigation switches between. Sorted in the
 * order the user is likely to want them: read first, then look at
 * pictures, then listen, then watch, then explore in 3D.
 */
private enum class Tab(
    val label: String,
    val icon: ImageVector,
    val kind: TimelineKind,
) {
    Info("Πληροφορίες", Icons.Outlined.Description, TimelineKind.TEXT),
    Image("Εικόνες", Icons.Filled.Image, TimelineKind.IMAGE),
    Audio("Ήχοι", Icons.Filled.AudioFile, TimelineKind.AUDIO),
    Video("Βίντεο", Icons.Filled.PlayCircleFilled, TimelineKind.VIDEO),
    Model("3D", Icons.Filled.ViewInAr, TimelineKind.MODEL),
}

@Composable
fun ContentPage(scene: ARSceneData, onBack: () -> Unit) {
    val available = remember(scene.card.id) {
        Tab.values().filter { tab -> scene.events.any { it.kind == tab.kind } }
    }
    var selected by remember(scene.card.id) {
        mutableStateOf(available.firstOrNull() ?: Tab.Info)
    }
    val eventsForTab = remember(scene.card.id, selected) {
        scene.events.filter { it.kind == selected.kind }.sortedBy { it.order }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding(),
    ) {
        HeroHeader(scene = scene, onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                },
                label = "tab",
            ) { tab ->
                TabContent(tab = tab, events = eventsForTab)
            }
        }
        BottomNav(
            available = available,
            selected = selected,
            onSelect = { selected = it },
        )
    }
}

/**
 * Polished header: marker thumbnail + title + subtitle + back button.
 * The marker thumbnail in the header is a strong visual anchor — the user
 * sees exactly what card they're inside.
 */
@Composable
private fun HeroHeader(scene: ARSceneData, onBack: () -> Unit) {
    Surface(
        color = Palette.Bg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Palette.Surface,
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, Palette.Border, CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Πίσω",
                        tint = Palette.OnSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            // Marker thumb
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val marker = scene.card.markerUrl
                if (!marker.isNullOrEmpty()) {
                    AsyncImage(
                        model = marker,
                        contentDescription = scene.card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = Palette.Gold.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.card.name,
                    color = Palette.OnSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                scene.card.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        color = Palette.OnSurfaceDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        // Subtle bottom divider so the header feels like its own zone.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Border),
        )
    }
}

/**
 * Custom bottom nav with an animated gold underline marker. Material's
 * NavigationBar feels too tall and noisy for our 2–5 tabs; this keeps the
 * chrome to a minimum.
 */
@Composable
private fun BottomNav(
    available: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    Surface(
        color = Palette.BgElev,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Top hairline divider for separation from content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Border),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (tab in available) {
                    NavItem(
                        tab = tab,
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val fgTarget = if (selected) Palette.Gold else Palette.OnSurfaceDim
    val fg by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = tween(200),
        label = "navItemFg",
    )
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = fgTarget.copy(alpha = fg),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = fgTarget.copy(alpha = fg),
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        // Indicator pill under the selected item, animated width.
        val indicatorWidth by animateFloatAsState(
            targetValue = if (selected) 22f else 0f,
            animationSpec = tween(220),
            label = "navIndicator",
        )
        Box(
            modifier = Modifier
                .width(indicatorWidth.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Palette.Gold),
        )
    }
}

@Composable
private fun TabContent(tab: Tab, events: List<ARTimelineEvent>) {
    if (events.isEmpty()) {
        EmptyState(message = "Δεν υπάρχει περιεχόμενο για ${tab.label.lowercase()}.")
        return
    }
    when (tab) {
        Tab.Info -> TextList(events)
        Tab.Image -> ImageList(events)
        Tab.Audio -> AudioList(events)
        Tab.Video -> VideoSingle(events.first())
        Tab.Model -> ModelSingle(events.first())
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Palette.OnSurfaceDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TextList(events: List<ARTimelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(events, key = { it.id }) { e ->
            Surface(
                color = Palette.Surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Palette.Border, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Subtle gold accent bar above each block — feels like a
                    // pulled-quote treatment.
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Palette.Gold),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = e.text.orEmpty(),
                        color = Palette.OnSurface,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageList(events: List<ARTimelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(events, key = { it.id }) { e ->
            val url = e.asset?.fileUrl
            if (!url.isNullOrEmpty()) {
                Surface(
                    color = Palette.Surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Palette.Border, RoundedCornerShape(18.dp)),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = e.asset.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Palette.SurfaceWarm, Palette.Bg),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioList(events: List<ARTimelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { e ->
            AudioRow(
                name = e.asset?.name ?: "Ήχος",
                url = e.asset?.fileUrl,
                loop = e.loop,
                autoplay = e.autoplay,
            )
        }
    }
}

@Composable
private fun AudioRow(name: String, url: String?, loop: Boolean, autoplay: Boolean) {
    if (url.isNullOrEmpty()) return
    var playing by remember(url) { mutableStateOf(false) }
    val mp = remember(url) {
        MediaPlayer().apply {
            setDataSource(url)
            isLooping = loop
            setOnPreparedListener {
                if (autoplay) { start(); playing = true }
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
    Surface(
        color = Palette.Surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.Border, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play / pause button — gold ring around tinted center for depth.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.GoldSoft)
                    .border(1.dp, Palette.Gold.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        try {
                            if (mp.isPlaying) { mp.pause(); playing = false }
                            else { mp.start(); playing = true }
                        } catch (_: Throwable) {}
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Παύση" else "Αναπαραγωγή",
                    tint = Palette.Gold,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Palette.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // Decorative waveform — purely visual.
                Waveform(playing = playing)
            }
        }
    }
}

@Composable
private fun Waveform(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "wf")
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 22 vertical bars at deterministic heights, animated subtly when playing.
        val heights = listOf(
            6f, 10f, 7f, 14f, 9f, 12f, 6f, 11f, 8f, 13f, 7f,
            10f, 6f, 12f, 9f, 14f, 7f, 11f, 6f, 10f, 8f, 12f,
        )
        for ((idx, base) in heights.withIndex()) {
            val anim by transition.animateFloat(
                initialValue = base,
                targetValue = if (playing) base * 1.4f else base,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500 + (idx * 17) % 400),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "wfBar$idx",
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(anim.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (playing) Palette.Gold.copy(alpha = 0.8f)
                        else Palette.OnSurfaceDim.copy(alpha = 0.4f),
                    ),
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun VideoSingle(e: ARTimelineEvent) {
    val url = e.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        EmptyState("Δεν υπάρχει διαθέσιμο βίντεο.")
        return
    }
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (e.loop) androidx.media3.common.Player.REPEAT_MODE_ONE
                else androidx.media3.common.Player.REPEAT_MODE_OFF
            playWhenReady = e.autoplay
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color.Black,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(1.dp, Palette.Border, RoundedCornerShape(18.dp)),
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

/**
 * 3D tab — hero "artifact" presentation + AR / 3D launchers that hand off
 * to Google's Scene Viewer. Avoiding inline Filament keeps this rock-solid.
 */
@Composable
private fun ModelSingle(e: ARTimelineEvent) {
    val url = e.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        EmptyState("Δεν υπάρχει 3D μοντέλο.")
        return
    }
    val context = LocalContext.current

    val launchSceneViewer: (String) -> Unit = { mode ->
        try {
            val sceneViewerUri = Uri.parse("https://arvr.google.com/scene-viewer/1.0")
                .buildUpon()
                .appendQueryParameter("file", url)
                .appendQueryParameter("mode", mode)
                .appendQueryParameter("title", e.asset.name)
                .build()
            val intent = Intent(Intent.ACTION_VIEW, sceneViewerUri).apply {
                setPackage("com.google.android.googlequicksearchbox")
            }
            context.startActivity(intent)
        } catch (e1: Throwable) {
            Log.w("ModelSingle", "Google app intent failed, falling back", e1)
            try {
                val genericUri = Uri.parse("https://arvr.google.com/scene-viewer/1.0")
                    .buildUpon()
                    .appendQueryParameter("file", url)
                    .appendQueryParameter("mode", mode)
                    .appendQueryParameter("title", e.asset.name)
                    .build()
                context.startActivity(Intent(Intent.ACTION_VIEW, genericUri))
            } catch (e2: Throwable) {
                Log.e("ModelSingle", "no scene viewer resolver", e2)
                Toast.makeText(
                    context,
                    "Δεν βρέθηκε Google Scene Viewer στο κινητό.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Hero artifact card
        Surface(
            color = Palette.SurfaceWarm,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Palette.Gold.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                .clickable { launchSceneViewer("3d_preferred") },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x55C9A86A),
                                Color(0x00000000),
                                Color(0x33000000),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(28.dp),
                ) {
                    // Soft glow halo behind the icon for depth.
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(Palette.GoldSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Palette.Gold.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ViewInAr,
                                contentDescription = null,
                                tint = Palette.Gold,
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = e.asset.name,
                        color = Palette.OnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "ΨΗΦΙΑΚΟ ΜΟΝΤΕΛΟ 3D",
                        color = Palette.Gold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        ActionButton(
            label = "Δες σε AR",
            sub = "Τοποθέτησε το μοντέλο στο χώρο σου",
            icon = Icons.Filled.ViewInAr,
            primary = true,
            onClick = { launchSceneViewer("ar_preferred") },
        )

        ActionButton(
            label = "Δες σε 3D",
            sub = "Περιστροφή, μεγέθυνση, λεπτομέρειες",
            icon = Icons.Filled.PlayCircleFilled,
            primary = false,
            onClick = { launchSceneViewer("3d_only") },
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    sub: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (primary) Palette.Gold else Palette.Surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, Palette.Border, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (primary) Color(0x33000000) else Palette.GoldSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (primary) Color(0xFF0A0A0A) else Palette.Gold,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (primary) Color(0xFF0A0A0A) else Palette.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sub,
                    color = if (primary) Color(0xCC000000) else Palette.OnSurfaceDim,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "→",
                color = if (primary) Color(0xFF0A0A0A) else Palette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
