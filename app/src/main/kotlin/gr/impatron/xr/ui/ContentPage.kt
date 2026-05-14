package gr.impatron.xr.ui

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * Logical buckets the bottom navigation switches between. We collapse all
 * text events under "Πληροφορίες", all images under "Εικόνες" etc., so the
 * nav stays tidy even when a card has many events of the same type.
 */
private enum class Tab(val label: String, val icon: String, val kind: TimelineKind) {
    Info("Πληροφορίες", "ⓘ", TimelineKind.TEXT),
    Image("Εικόνες", "▣", TimelineKind.IMAGE),
    Audio("Ήχοι", "♪", TimelineKind.AUDIO),
    Video("Βίντεο", "▶", TimelineKind.VIDEO),
    Model("3D", "◈", TimelineKind.MODEL),
}

@Composable
fun ContentPage(scene: ARSceneData, onBack: () -> Unit) {
    // Which tabs have any events? We don't show empty tabs in the nav.
    val available = remember(scene.card.id) {
        Tab.values().filter { tab ->
            scene.events.any { it.kind == tab.kind }
        }
    }
    var selected by remember(scene.card.id) {
        mutableStateOf(available.firstOrNull() ?: Tab.Info)
    }
    val eventsForTab = remember(scene.card.id, selected) {
        scene.events
            .filter { it.kind == selected.kind }
            .sortedBy { it.order }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding(),
    ) {
        HeaderBar(scene = scene, onBack = onBack)
        // The animated tab content fills the remaining space above the nav.
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
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

@Composable
private fun HeaderBar(scene: ARSceneData, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xFF161616),
            shape = CircleShape,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onBack),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "←",
                    color = Color(0xFFF5F1E8),
                    fontSize = 18.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scene.card.name,
                color = Color(0xFFF5F1E8),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            scene.card.subtitle?.let { sub ->
                Text(
                    text = sub,
                    color = Color(0xFFA9A59C),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BottomNav(
    available: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    Surface(
        color = Color(0xFF111111),
        modifier = Modifier.fillMaxWidth(),
    ) {
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

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color(0xFFC9A86A) else Color(0xFFA9A59C)
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tab.icon,
            color = fg,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = fg,
            fontSize = 10.sp,
            letterSpacing = 0.3.sp,
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
            color = Color(0xFFA9A59C),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TextList(events: List<ARTimelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { e ->
            Surface(
                color = Color(0xFF161616),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = e.text.orEmpty(),
                    color = Color(0xFFF5F1E8),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageList(events: List<ARTimelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(events, key = { it.id }) { e ->
            val url = e.asset?.fileUrl
            if (!url.isNullOrEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = e.asset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D)),
                            ),
                        ),
                )
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
                if (autoplay) {
                    start(); playing = true
                }
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
        color = Color(0xFF161616),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x33C9A86A))
                    .clickable {
                        try {
                            if (mp.isPlaying) {
                                mp.pause(); playing = false
                            } else {
                                mp.start(); playing = true
                            }
                        } catch (_: Throwable) {}
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (playing) "❚❚" else "▶",
                    color = Color(0xFFC9A86A),
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                color = Color(0xFFF5F1E8),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
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

@Composable
private fun ModelSingle(e: ARTimelineEvent) {
    val url = e.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        EmptyState("Δεν υπάρχει 3D μοντέλο.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D)),
                    ),
                ),
        ) {
            InlineModelViewer(
                name = e.asset.name,
                url = url,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = e.asset.name,
            color = Color(0xFFA9A59C),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
