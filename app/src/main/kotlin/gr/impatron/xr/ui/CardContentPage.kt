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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import gr.impatron.xr.ARTimelineEvent
import gr.impatron.xr.TimelineKind

/**
 * Browse a single card's events without scanning the physical card. Same
 * tap → confirm → viewer pipeline as the AR scanner; this page is the
 * accessibility entry point ('I don't have the printed card on me, just
 * let me see what's there'). Reached from the Intro list.
 */
@Composable
fun CardContentPage(
    scene: ARSceneData,
    onBack: () -> Unit,
    onEventTapped: (ARTimelineEvent) -> Unit,
) {
    val events = remember(scene.card.id) {
        scene.events.sortedBy { it.order }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding(),
    ) {
        Hero(scene = scene, onBack = onBack)
        if (events.isEmpty()) {
            EmptyEvents()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    EventRow(event = event, onClick = { onEventTapped(event) })
                }
            }
        }
    }
}

@Composable
private fun Hero(scene: ARSceneData, onBack: () -> Unit) {
    Column {
        // Top bar: back arrow + small marker thumb
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
                    .size(42.dp)
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
        }

        // Hero: marker thumb + title + subtitle
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.SurfaceWarm)
                    .border(1.dp, Palette.Border, RoundedCornerShape(20.dp)),
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
                            .clip(RoundedCornerShape(20.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = Palette.Gold.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "ΕΚΘΕΜΑ",
                color = Palette.Gold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = scene.card.name,
                color = Palette.OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
            )
            scene.card.subtitle?.let { sub ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    color = Palette.OnSurfaceDim,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "ΠΕΡΙΕΧΟΜΕΝΟ",
                color = Palette.OnSurfaceMute,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun EventRow(event: ARTimelineEvent, onClick: () -> Unit) {
    val (icon, label) = remember(event.id) { kindLabel(event) }
    val preview = remember(event.id) { previewFor(event) }
    Surface(
        color = Palette.Surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kind icon — gold-tinted disc
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.GoldSoft)
                    .border(1.dp, Palette.Gold.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Palette.Gold,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Palette.Gold,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    color = Palette.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Palette.OnSurfaceDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun kindLabel(event: ARTimelineEvent): Pair<ImageVector, String> = when (event.kind) {
    TimelineKind.TEXT -> Icons.Outlined.Description to "ΚΕΙΜΕΝΟ"
    TimelineKind.IMAGE -> Icons.Filled.Image to "ΕΙΚΟΝΑ"
    TimelineKind.AUDIO -> Icons.Filled.AudioFile to "ΗΧΟΣ"
    TimelineKind.VIDEO -> Icons.Filled.PlayCircleFilled to "ΒΙΝΤΕΟ"
    TimelineKind.MODEL -> Icons.Filled.ViewInAr to "3D ΜΟΝΤΕΛΟ"
    TimelineKind.UNKNOWN -> Icons.Outlined.Description to "ΥΛΙΚΟ"
}

private fun previewFor(event: ARTimelineEvent): String = when (event.kind) {
    TimelineKind.TEXT -> event.text?.takeIf { it.isNotBlank() } ?: "Πληροφορία"
    else -> event.asset?.name?.takeIf { it.isNotBlank() } ?: when (event.kind) {
        TimelineKind.IMAGE -> "Εικόνα"
        TimelineKind.AUDIO -> "Ήχος"
        TimelineKind.VIDEO -> "Βίντεο"
        TimelineKind.MODEL -> "3D μοντέλο"
        else -> "Υλικό"
    }
}

@Composable
private fun EmptyEvents() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Palette.GoldSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Palette.Gold,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Δεν υπάρχει υλικό για αυτή την κάρτα ακόμη.",
                color = Palette.OnSurfaceDim,
                fontSize = 13.sp,
            )
        }
    }
}
