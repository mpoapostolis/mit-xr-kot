package gr.impatron.xr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

@Serializable
data class ARCardDto(
    val id: String,
    val name: String = "",
    val subtitle: String? = null,
    val target_index: Int = 0,
    val marker: String = "",
    val mind_file: String = "",
    val published: Boolean = false,
)

@Serializable
data class ARAssetDto(
    val id: String,
    val name: String = "",
    val kind: String = "image",
    val file: String = "",
)

@Serializable
data class ARTimelineDto(
    val id: String,
    val card: String,
    val kind: String,
    val asset: String? = null,
    val text: String? = null,
    val start: Float = 0f,
    val end: Float = 10f,
    val position: Vec3 = Vec3(),
    val rotation: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f),
    val loop: Boolean = false,
    val autoplay: Boolean = true,
    val order: Int = 0,
)

@Serializable
data class PbList<T>(
    val items: List<T> = emptyList(),
    @SerialName("totalItems") val totalItems: Int = 0,
)

/** Resolved scene model used by the AR layer. */
data class ARCard(
    val id: String,
    val name: String,
    val subtitle: String?,
    val targetIndex: Int,
    val markerUrl: String?,
    val published: Boolean,
)

data class ARAsset(
    val id: String,
    val name: String,
    val kind: AssetKind,
    val fileUrl: String,
)

enum class AssetKind { IMAGE, AUDIO, MODEL, VIDEO, MIND, UNKNOWN }

fun assetKindFrom(raw: String): AssetKind = when (raw.lowercase()) {
    "image" -> AssetKind.IMAGE
    "audio" -> AssetKind.AUDIO
    "model" -> AssetKind.MODEL
    "video" -> AssetKind.VIDEO
    "mind" -> AssetKind.MIND
    else -> AssetKind.UNKNOWN
}

enum class TimelineKind { TEXT, IMAGE, AUDIO, MODEL, VIDEO, UNKNOWN }

fun timelineKindFrom(raw: String): TimelineKind = when (raw.lowercase()) {
    "text" -> TimelineKind.TEXT
    "image" -> TimelineKind.IMAGE
    "audio" -> TimelineKind.AUDIO
    "model" -> TimelineKind.MODEL
    "video" -> TimelineKind.VIDEO
    else -> TimelineKind.UNKNOWN
}

data class ARTimelineEvent(
    val id: String,
    val cardId: String,
    val kind: TimelineKind,
    val asset: ARAsset?,
    val text: String?,
    val start: Float,
    val end: Float,
    val position: Vec3,
    val rotation: Vec3,
    val scale: Vec3,
    val loop: Boolean,
    val autoplay: Boolean,
    val order: Int,
)

data class ARSceneData(
    val card: ARCard,
    val events: List<ARTimelineEvent>,
    val duration: Float,
)

data class ARPack(val scenes: List<ARSceneData>)
