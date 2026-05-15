package gr.impatron.xr.ui

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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Image as IconImage
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.impatron.xr.ARSceneData
import gr.impatron.xr.TimelineKind

/**
 * Landing page. The user starts here. Two paths to the same destination:
 *   1. Big primary CTA → opens the AR scanner.
 *   2. Tap a card from the gallery → skips the scan and goes straight to
 *      content. Useful when the printed marker isn't to hand.
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
            .background(Palette.Bg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroHeader() }
        item { ScanCTA(onScan = onScan) }
        if (scenes.isNotEmpty()) {
            item { SectionLabel("Διαθέσιμες κάρτες", count = scenes.size) }
            items(scenes, key = { it.card.id }) { scene ->
                CardListRow(scene = scene, onClick = { onPickCard(scene) })
            }
        }
        item { Footer() }
    }
}

@Composable
private fun HeroHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        // Brand mark — small monogram circle + uppercased name.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Palette.GoldSoft)
                    .border(1.dp, Palette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ι",
                    color = Palette.Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Ι.Μ. ΠΑΤΡΩΝ",
                color = Palette.Gold,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Ψηφιακές\nΠαρουσιάσεις",
            color = Palette.OnSurface,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 40.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Σκάναρε μια κάρτα με την κάμερα ή διάλεξε απευθείας από τη συλλογή.",
            color = Palette.OnSurfaceDim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun ScanCTA(onScan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Palette.Gold,
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = Color(0xFF0A0A0A),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Σκάναρε κάρτα",
                    color = Color(0xFF0A0A0A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Άνοιξε την κάμερα",
                    color = Color(0xCC000000),
                    fontSize = 12.sp,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF0A0A0A),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Row(
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Palette.OnSurfaceDim,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(Palette.Border),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = count.toString(),
            color = Palette.OnSurfaceMute,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun CardListRow(scene: ARSceneData, onClick: () -> Unit) {
    Surface(
        color = Palette.Surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Marker thumbnail — square with subtle inner border for depth.
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.BgElev)
                    .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)),
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
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = Palette.Gold.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.card.name,
                    color = Palette.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                scene.card.subtitle?.let { sub ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        color = Palette.OnSurfaceDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
                EventBadges(scene)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Palette.OnSurfaceDim,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp),
            )
        }
    }
}

/**
 * Tiny inline badges showing which media types this card has. Gives the user
 * a glance at what they're going to find before tapping in.
 */
@Composable
private fun EventBadges(scene: ARSceneData) {
    val kinds = androidx.compose.runtime.remember(scene.card.id) {
        scene.events.map { it.kind }.distinct()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (kind in kinds) {
            val (icon, label) = kindMeta(kind) ?: continue
            Surface(
                color = Palette.GoldSoft,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Palette.Gold,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = label,
                        color = Palette.Gold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun kindMeta(kind: TimelineKind): Pair<ImageVector, String>? = when (kind) {
    TimelineKind.TEXT -> Icons.Outlined.Description to "ΚΕΙΜΕΝΟ"
    TimelineKind.IMAGE -> Icons.Filled.Image to "ΕΙΚΟΝΑ"
    TimelineKind.AUDIO -> Icons.Filled.AudioFile to "ΗΧΟΣ"
    TimelineKind.VIDEO -> Icons.Filled.PlayCircleFilled to "ΒΙΝΤΕΟ"
    TimelineKind.MODEL -> Icons.Filled.ViewInAr to "3D"
    TimelineKind.UNKNOWN -> null
}

@Composable
private fun Footer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Palette.Border),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Ιερά Μητρόπολη Πατρών",
            color = Palette.OnSurfaceMute,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Palette.Border),
        )
    }
}
