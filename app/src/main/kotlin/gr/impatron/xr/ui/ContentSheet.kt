package gr.impatron.xr.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@Composable
fun ContentSheet(
    scene: ARSceneData,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE0A0A0A))
            .systemBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 72.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = scene.card.name,
                        color = Color(0xFFC9A86A),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    scene.card.subtitle?.let { sub ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sub,
                            color = Color(0xFFA9A59C),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Events
            items(scene.events.sortedBy { it.order }) { event ->
                EventRow(event)
            }
        }

        // Close button (top-right)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 12.dp, end = 12.dp)
                .size(44.dp)
                .background(Color(0xCC161616), CircleShape)
                .align(Alignment.TopEnd),
        ) {
            Text(
                text = "✕",
                color = Color(0xFFF5F1E8),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EventRow(event: ARTimelineEvent) {
    when (event.kind) {
        TimelineKind.TEXT -> TextCard(event.text ?: "")
        TimelineKind.IMAGE -> ImageCard(event.asset?.fileUrl)
        TimelineKind.AUDIO -> AudioCard(
            event.asset?.name ?: "Ήχος",
            event.asset?.fileUrl,
            event.loop,
            event.autoplay,
        )
        TimelineKind.VIDEO -> VideoCard(event.asset?.fileUrl, event.loop, event.autoplay)
        TimelineKind.MODEL -> ModelCard(
            event.asset?.name ?: "Μοντέλο",
            event.asset?.fileUrl,
        )
        else -> {}
    }
}

@Composable
private fun TextCard(text: String) {
    Surface(
        color = Color(0xCC161616),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = Color(0xFFF5F1E8),
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ImageCard(url: String?) {
    if (url.isNullOrEmpty()) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33000000)),
    )
}

@Composable
private fun AudioCard(name: String, url: String?, loop: Boolean, autoplay: Boolean) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val mp = remember(url) {
        if (url.isNullOrEmpty()) {
            null
        } else {
            MediaPlayer().apply {
                setDataSource(url)
                isLooping = loop
                setOnPreparedListener {
                    if (autoplay) {
                        start()
                        playing = true
                    }
                }
                setOnCompletionListener { playing = false }
                prepareAsync()
            }
        }
    }
    DisposableEffect(mp) {
        onDispose {
            try {
                mp?.stop()
                mp?.release()
            } catch (_: Exception) {}
        }
    }
    Surface(
        color = Color(0xCC161616),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val player = mp ?: return@IconButton
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        player.start()
                        playing = true
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Text(
                    text = if (playing) "❚❚" else "▶",
                    color = Color(0xFFC9A86A),
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = name,
                color = Color(0xFFF5F1E8),
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun VideoCard(url: String?, loop: Boolean, autoplay: Boolean) {
    if (url.isNullOrEmpty()) return
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) androidx.media3.common.Player.REPEAT_MODE_ONE
                else androidx.media3.common.Player.REPEAT_MODE_OFF
            playWhenReady = autoplay
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
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

@Composable
private fun ModelCard(name: String, url: String?) {
    if (url.isNullOrEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xCC161616)),
        ) {
            InlineModelViewer(
                name = name,
                url = url,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = name,
            color = Color(0xFFA9A59C),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
