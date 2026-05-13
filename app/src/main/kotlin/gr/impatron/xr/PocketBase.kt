package gr.impatron.xr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PB {
    const val URL = "https://yms.galerra.art"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun fileUrl(collection: String, recordId: String, filename: String): String =
        "$URL/api/files/$collection/$recordId/$filename"

    private suspend inline fun <reified T> getList(
        collection: String,
        filter: String? = null,
        sort: String? = null,
    ): PbList<T> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$URL/api/collections/$collection/records?perPage=500")
            if (filter != null) append("&filter=").append(java.net.URLEncoder.encode(filter, "UTF-8"))
            if (sort != null) append("&sort=").append(java.net.URLEncoder.encode(sort, "UTF-8"))
        }
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string()
                ?: throw IllegalStateException("Empty response from $url")
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $body")
            json.decodeFromString<PbList<T>>(body)
        }
    }

    /**
     * Fetch all published cards + all timeline events + all assets, resolve
     * relations and return a fully-typed AR pack ready to render.
     */
    suspend fun fetchPack(): ARPack {
        val cards = getList<ARCardDto>("ar_cards", sort = "target_index").items
        val timeline = getList<ARTimelineDto>("ar_timeline", sort = "card,order,start").items
        val assets = getList<ARAssetDto>("ar_assets").items

        val published = cards.filter { it.published }
        if (cards.isNotEmpty() && published.isEmpty()) {
            throw IllegalStateException(
                "Υπάρχουν κάρτες αλλά καμία δημοσιευμένη. " +
                    "Στο admin πάτησε «Δημοσιευμένη».",
            )
        }

        val assetById = assets.associate { a ->
            a.id to ARAsset(
                id = a.id,
                name = a.name,
                kind = assetKindFrom(a.kind),
                fileUrl = if (a.file.isNotEmpty()) fileUrl("ar_assets", a.id, a.file) else "",
            )
        }

        val eventsByCard = timeline.groupBy { it.card }.mapValues { (_, evts) ->
            evts.map { t ->
                ARTimelineEvent(
                    id = t.id,
                    cardId = t.card,
                    kind = timelineKindFrom(t.kind),
                    asset = t.asset?.let { assetById[it] },
                    text = t.text,
                    start = t.start,
                    end = if (t.end > 0f) t.end else 10f,
                    position = t.position,
                    rotation = t.rotation,
                    scale = t.scale,
                    loop = t.loop,
                    autoplay = t.autoplay,
                    order = t.order,
                )
            }
        }

        val scenes = published.map { c ->
            val card = ARCard(
                id = c.id,
                name = c.name,
                subtitle = c.subtitle?.takeIf { it.isNotBlank() },
                targetIndex = c.target_index,
                markerUrl = if (c.marker.isNotEmpty()) fileUrl("ar_cards", c.id, c.marker) else null,
                published = c.published,
            )
            val evts = eventsByCard[card.id].orEmpty()
            val dur = evts.maxOfOrNull { it.end } ?: 0f
            ARSceneData(card = card, events = evts, duration = dur)
        }

        return ARPack(scenes = scenes)
    }

    /** Download a remote file (marker, glb, etc.) into a byte array. */
    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code} for $url")
            res.body?.bytes() ?: ByteArray(0)
        }
    }
}
