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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

/** Walks the ContextWrapper chain to find the hosting Activity. Compose's
 *  LocalView.context is sometimes wrapped (e.g. by ContextThemeWrapper),
 *  so a plain `as? Activity` cast returns null. */
private fun unwrapActivity(ctx: Context): Activity? {
    var c: Context? = ctx
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

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

    // Immersive while watching: hide status + nav bars so the video gets
    // every pixel. Wrapped defensively — on some devices the
    // WindowInsetsController can be null mid-transition (e.g. when the
    // Scanner is still tearing down), and a NullPointer or
    // NoSuchMethodError here would crash the whole player.
    DisposableEffect(Unit) {
        val activity = (view.context as? Activity)
            ?: unwrapActivity(view.context)
        val window = activity?.window
        val controller = try {
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, view)
            } else null
        } catch (e: Throwable) {
            Log.w("VideoPlayerPage", "getInsetsController failed", e)
            null
        }
        try {
            controller?.let {
                it.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Throwable) {
            Log.w("VideoPlayerPage", "immersive enter failed", e)
        }
        onDispose {
            try {
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } catch (e: Throwable) {
                Log.w("VideoPlayerPage", "immersive exit failed", e)
            }
        }
    }

    // Show built-in controls right when the page mounts. PlayerView's
    // controller is its own gesture-aware overlay; we don't fight it.
    var controlsShown by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        try {
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        } catch (e: Throwable) {
                            Log.w("VideoPlayerPage", "setShowBuffering failed", e)
                        }
                        // ControllerVisibilityListener exists in media3 1.4+
                        // but its signature differs slightly across patch
                        // versions; wrap so a NoSuchMethodError doesn't kill
                        // the whole player.
                        try {
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { v ->
                                    controlsShown = v == android.view.View.VISIBLE
                                },
                            )
                        } catch (e: Throwable) {
                            Log.w("VideoPlayerPage", "visibility listener wiring failed", e)
                            // Fallback: leave the back chip visible all
                            // the time so the user always has a way out.
                            controlsShown = true
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("VideoPlayerPage", "PlayerView factory failed", e)
                    // Return an empty View so AndroidView doesn't NPE; the
                    // user will see a black screen + back chip.
                    android.widget.FrameLayout(ctx)
                }
            },
            update = { view ->
                (view as? PlayerView)?.player = player
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
