package gr.impatron.xr.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.impatron.xr.ARSceneData

/**
 * Landing page. Lets the user either jump straight to the AR scanner, or
 * pick a card from the gallery (which is a faster path when they already
 * know what they want to see and don't have the printed marker handy).
 */
@Composable
fun IntroPage(
    scenes: List<ARSceneData>,
    onScan: () -> Unit,
    onPickCard: (ARSceneData) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .systemBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 28.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroHeader() }
        item { ScanCTA(onScan = onScan) }
        if (scenes.isNotEmpty()) {
            item { SectionLabel("Διαθέσιμες κάρτες") }
            items(scenes, key = { it.card.id }) { scene ->
                CardListRow(scene = scene, onClick = { onPickCard(scene) })
            }
        }
    }
}

@Composable
private fun HeroHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 18.dp),
    ) {
        Text(
            text = "Ι.Μ. ΠΑΤΡΩΝ",
            color = Color(0xFFC9A86A),
            fontSize = 14.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Ψηφιακές\nΠαρουσιάσεις",
            color = Color(0xFFF5F1E8),
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 38.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Σκάναρε μια κάρτα ή διάλεξε από τη λίστα παρακάτω.",
            color = Color(0xFFA9A59C),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ScanCTA(onScan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFC9A86A),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onScan),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⎙", fontSize = 22.sp, color = Color(0xFF0A0A0A))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Σκάναρε κάρτα",
                    color = Color(0xFF0A0A0A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Άνοιξε την κάμερα",
                    color = Color(0x99000000),
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "→",
                color = Color(0xFF0A0A0A),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFFA9A59C),
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun CardListRow(scene: ARSceneData, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF161616),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Marker thumbnail with a subtle gold edge gradient for warmth.
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2620), Color(0xFF1A1814)),
                        ),
                    ),
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
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text(text = "🪧", color = Color(0xFFC9A86A), fontSize = 32.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.card.name,
                    color = Color(0xFFF5F1E8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                scene.card.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        color = Color(0xFFA9A59C),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                val n = scene.events.size
                Text(
                    text = "$n στοιχεί" + (if (n == 1) "ο" else "α"),
                    color = Color(0xFFC9A86A),
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "›",
                color = Color(0xFFA9A59C),
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
