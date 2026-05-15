package gr.impatron.xr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import gr.impatron.xr.ARTimelineEvent

/**
 * Standalone fullscreen video player. The previous Compose Dialog wasn't
 * "really" a separate view — the AR scene kept rendering underneath, the
 * status bar still belonged to the AR layer, controls felt cramped. This
 * mounts as its own Page so the AR view fully unmounts, ExoPlayer gets
 * edge-to-edge bleed, and the back arrow is the only piece of in-app
 * chrome (PlayerView provides its own playback controls).
 */
@Composable
fun VideoPlayerPage(event: ARTimelineEvent, onBack: () -> Unit) {
    val url = event.asset?.fileUrl
    if (url.isNullOrEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val view = LocalView.current
    val player = remember(url) {
        try {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = if (event.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
        } catch (e: Throwable) {
            Log.e("VideoPlayerPage", "ExoPlayer build failed for $url", e)
            null
        }
    }
    DisposableEffect(player) {
        onDispose {
            try {
                player?.stop()
                player?.release()
            } catch (e: Throwable) {
                Log.w("VideoPlayerPage", "player release failed", e)
            }
        }
    }
    if (player == null) {
        LaunchedEffect(url) { onBack() }
        return
    }

    // Show built-in controls right when the page mounts. PlayerView's
    // controller is its own gesture-aware overlay; we don't fight it.
    var controlsShown by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    // Bridge controller visibility into Compose so we can
                    // fade the back chip in/out alongside the native bar.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsShown = visibility == android.view.View.VISIBLE
                        },
                    )
                }
            },
            update = { pv ->
                // No-op for now — keeps Compose happy on recomposition.
                pv.player = player
            },
        )

        // Back chip — only visible alongside PlayerView controls so video
        // can stay edge-to-edge during playback.
        if (controlsShown) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
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
                Column {
                    Text(
                        text = "ΒΙΝΤΕΟ",
                        color = Palette.Gold,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = event.asset?.name ?: "Βίντεο",
                        color = Palette.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
