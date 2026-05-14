package gr.impatron.xr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val AppColors = darkColorScheme(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF0A0A0A),
    primary = Color(0xFFC9A86A),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A),
                ) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
private fun AppScreen() {
    var status by remember { mutableStateOf("Φόρτωση σκηνών…") }
    var pack by remember { mutableStateOf<ARPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeScene by remember { mutableStateOf<ARSceneData?>(null) }

    LaunchedEffect(Unit) {
        try {
            val p = PB.fetchPack()
            if (p.scenes.isEmpty()) {
                error = "Δεν υπάρχουν δημοσιευμένες κάρτες."
                return@LaunchedEffect
            }
            pack = p
            status = ""
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

    val active = activeScene
    if (active == null) {
        CardList(scenes = resolved.scenes, onPick = { activeScene = it })
    } else {
        gr.impatron.xr.ui.ContentSheet(
            scene = active,
            onDismiss = { activeScene = null },
        )
    }
}

@Composable
private fun CardList(scenes: List<ARSceneData>, onPick: (ARSceneData) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Ι.Μ. Πατρών",
                    color = Color(0xFFC9A86A),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Διάλεξε μια κάρτα για να δεις το περιεχόμενο",
                    color = Color(0xFFA9A59C),
                    fontSize = 13.sp,
                )
            }
        }
        items(scenes, key = { it.card.id }) { scene ->
            CardRow(scene = scene, onClick = { onPick(scene) })
        }
    }
}

@Composable
private fun CardRow(scene: ARSceneData, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF161616),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF222222)),
            ) {
                val marker = scene.card.markerUrl
                if (!marker.isNullOrEmpty()) {
                    AsyncImage(
                        model = marker,
                        contentDescription = scene.card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "🪧",
                            fontSize = 36.sp,
                            color = Color(0xFFC9A86A),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = scene.card.name,
                    color = Color(0xFFF5F1E8),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                scene.card.subtitle?.let { sub ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        color = Color(0xFFA9A59C),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val n = scene.events.size
                Text(
                    text = "$n στοιχεί" + (if (n == 1) "ο" else "α"),
                    color = Color(0xFFC9A86A),
                    fontSize = 12.sp,
                )
            }
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
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = Color(0xFFA9A59C),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
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
