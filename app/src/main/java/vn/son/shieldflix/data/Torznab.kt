package vn.son.shieldflix.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import kotlin.math.ln

/**
 * Tìm torrent qua chuẩn Torznab (Jackett, Prowlarr…). Người dùng tự cấu hình
 * nguồn trong phần Cài đặt; app chỉ đọc kết quả, xếp theo seed và chất lượng.
 */
class TorznabClient(private val prefs: Prefs) {

    suspend fun searchMovie(imdbId: String?, title: String, originalTitle: String, year: String): List<TorrentResult> {
        val queries = mutableListOf<Map<String, String>>()
        if (imdbId != null) queries += mapOf("t" to "movie", "imdbid" to imdbId)
        queries += mapOf("t" to "search", "q" to "$originalTitle $year".trim(), "cat" to "2000")
        if (title != originalTitle) queries += mapOf("t" to "search", "q" to "$title $year".trim(), "cat" to "2000")
        val results = runAll(queries)
        // Kết quả tìm theo tên phải khớp năm để tránh nhầm phim cùng tên.
        return rank(results.filter { r ->
            year.isBlank() || r.title.contains(year) || Regex("(19|20)\\d{2}").find(r.title) == null
        }, season = 0, episode = 0)
    }

    suspend fun searchEpisode(imdbId: String?, title: String, originalTitle: String, season: Int, episode: Int): List<TorrentResult> {
        val se = "S%02dE%02d".format(season, episode)
        val queries = mutableListOf<Map<String, String>>()
        if (imdbId != null) queries += mapOf("t" to "tvsearch", "imdbid" to imdbId, "season" to "$season", "ep" to "$episode")
        queries += mapOf("t" to "search", "q" to "$originalTitle $se", "cat" to "5000")
        // Gói cả mùa (season pack)
        queries += mapOf("t" to "search", "q" to "$originalTitle S%02d".format(season), "cat" to "5000")
        if (imdbId != null) queries += mapOf("t" to "tvsearch", "imdbid" to imdbId, "season" to "$season")
        return rank(runAll(queries), season, episode)
    }

    private suspend fun runAll(queries: List<Map<String, String>>): List<TorrentResult> = coroutineScope {
        val urls = prefs.torznabUrls
        if (urls.isEmpty()) throw IllegalStateException("Chưa cấu hình nguồn Torznab (Jackett/Prowlarr) trong Cài đặt")
        val jobs = urls.flatMap { base -> queries.map { q -> async { safeQuery(base, q) } } }
        val all = jobs.awaitAll().flatten()
        if (all.isEmpty() && jobs.isNotEmpty()) {
            // Không có kết quả: thử lại một truy vấn để lấy lỗi cụ thể báo cho người dùng.
            query(urls.first(), queries.last())
        }
        // Gộp trùng theo infohash hoặc tên.
        all.groupBy { (it.infoHash ?: it.title).lowercase() }.map { (_, list) -> list.maxBy { it.seeders } }
    }

    private suspend fun safeQuery(base: String, params: Map<String, String>): List<TorrentResult> =
        try { query(base, params) } catch (e: Exception) { emptyList() }

    private suspend fun query(base: String, params: Map<String, String>): List<TorrentResult> {
        val sep = if (base.contains("?")) "&" else "?"
        val qs = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val xml = Net.get("$base$sep$qs")
        if (xml.contains("<error")) {
            val msg = Regex("description=\"([^\"]*)\"").find(xml)?.groupValues?.get(1) ?: "lỗi Torznab"
            throw IOException(msg)
        }
        return withContext(Dispatchers.Default) { parse(xml, hostOf(base)) }
    }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore("/").substringBefore(":")

    private fun parse(xml: String, fallbackIndexer: String): List<TorrentResult> {
        val out = mutableListOf<TorrentResult>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        var inItem = false
        var title = ""; var size = 0L; var seeders = -1; var peers = 0
        var magnet: String? = null; var link: String? = null; var hash: String? = null; var indexer = fallbackIndexer
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val name = p.name
                when {
                    name == "item" -> {
                        inItem = true
                        title = ""; size = 0L; seeders = -1; peers = 0; magnet = null; link = null; hash = null
                        indexer = fallbackIndexer
                    }
                    !inItem -> {}
                    name == "title" -> title = p.nextText().trim()
                    name == "size" -> size = p.nextText().trim().toLongOrNull() ?: size
                    name == "link" -> link = p.nextText().trim()
                    name == "jackettindexer" || name == "prowlarrindexer" -> indexer = p.nextText().trim()
                    name == "enclosure" -> {
                        val u = p.getAttributeValue(null, "url")
                        if (u != null) { if (u.startsWith("magnet:")) magnet = u else if (link == null) link = u }
                        p.getAttributeValue(null, "length")?.toLongOrNull()?.let { if (size == 0L) size = it }
                    }
                    name.endsWith("attr") -> {
                        val n = p.getAttributeValue(null, "name")
                        val v = p.getAttributeValue(null, "value") ?: ""
                        when (n) {
                            "seeders" -> seeders = v.toIntOrNull() ?: seeders
                            "peers" -> peers = v.toIntOrNull() ?: peers
                            "magneturl" -> if (v.startsWith("magnet:")) magnet = v
                            "infohash" -> hash = v.lowercase()
                            "size" -> if (size == 0L) size = v.toLongOrNull() ?: 0L
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                inItem = false
                if (link?.startsWith("magnet:") == true) { magnet = magnet ?: link }
                if (hash == null && magnet != null) {
                    hash = Regex("btih:([a-zA-Z0-9]+)").find(magnet!!)?.groupValues?.get(1)?.lowercase()
                }
                if (title.isNotBlank() && (magnet != null || link != null)) {
                    out += TorrentResult(
                        title = title, sizeBytes = size, seeders = maxOf(seeders, 0), peers = peers,
                        magnet = magnet, link = link, infoHash = hash, indexer = indexer,
                        quality = Quality.of(title), score = 0.0, vietsub = Quality.isVietnamese(title),
                    )
                }
            }
            ev = p.next()
        }
        return out
    }

    /** Xếp hạng: nhiều seed + đúng chất lượng ưa thích + có Vietsub; loại CAM và bản 0 seed. */
    fun rank(list: List<TorrentResult>, season: Int, episode: Int): List<TorrentResult> {
        val pref = prefs.preferredQuality
        val epRe = if (season > 0) Quality.episodeRegex(season, episode) else null
        val packRe = if (season > 0) Quality.seasonPackRegex(season) else null
        return list.asSequence()
            .filter { it.seeders > 0 }
            .filter { !prefs.hideCam || !Quality.isCam(it.title) }
            .filter { r ->
                if (season == 0) true
                else epRe!!.containsMatchIn(r.title) || (packRe!!.containsMatchIn(r.title) && !Quality.hasAnyEpisode(r.title))
            }
            .map { r ->
                var s = ln(r.seeders + 1.0) * 12
                s += when {
                    r.quality == pref -> 25.0
                    pref == "2160p" && r.quality == "1080p" -> 15.0
                    pref == "1080p" && r.quality == "720p" -> 10.0
                    pref == "720p" && r.quality == "1080p" -> 8.0
                    r.quality == "SD" -> -10.0
                    else -> 0.0
                }
                if (r.vietsub) s += 20.0
                if (Quality.isCam(r.title)) s -= 60.0
                // Bản remux quá lớn stream qua mạng dễ giật.
                if (r.sizeBytes > 40L * 1_073_741_824) s -= 20.0
                if (season > 0 && epRe!!.containsMatchIn(r.title)) s += 10.0 // tập lẻ tải nhanh hơn cả mùa
                r.copy(score = s)
            }
            .sortedByDescending { it.score }
            .toList()
    }

    /**
     * Biến một kết quả thành nguồn mở được: magnet, hoặc tải file .torrent
     * (Jackett có thể chuyển hướng link tải sang magnet).
     */
    suspend fun resolve(r: TorrentResult): TorrentSource = withContext(Dispatchers.IO) { resolveBlocking(r) }

    /** Chạy trên luồng IO: đọc body mạng trên luồng chính sẽ lỗi NetworkOnMainThread. */
    private fun resolveBlocking(r: TorrentResult): TorrentSource {
        r.magnet?.let { return TorrentSource.Magnet(it) }
        var url = r.link ?: throw IOException("Kết quả không có link")
        repeat(5) {
            val resp = Net.noRedirect.newCall(Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build()).execute()
            resp.use { res ->
                if (res.isRedirect) {
                    val loc = res.header("Location") ?: throw IOException("Chuyển hướng không có địa chỉ")
                    if (loc.startsWith("magnet:")) return TorrentSource.Magnet(loc)
                    url = res.request.url.resolve(loc)?.toString() ?: loc
                } else {
                    if (!res.isSuccessful) throw IOException("Tải .torrent lỗi HTTP ${res.code}")
                    val bytes = res.body?.bytes() ?: ByteArray(0)
                    if (bytes.isNotEmpty() && bytes[0] == 'd'.code.toByte()) {
                        val fb = r.infoHash?.let { "magnet:?xt=urn:btih:$it" }
                        return TorrentSource.FileBytes(bytes, fb)
                    }
                    val text = String(bytes).trim()
                    if (text.startsWith("magnet:")) return TorrentSource.Magnet(text)
                    throw IOException("Link không phải file torrent")
                }
            }
        }
        throw IOException("Chuyển hướng quá nhiều lần")
    }
}

object Quality {
    fun of(t: String): String {
        val s = t.lowercase()
        return when {
            Regex("2160p|\\b4k\\b|uhd").containsMatchIn(s) -> "2160p"
            s.contains("1080p") || s.contains("1080i") -> "1080p"
            s.contains("720p") -> "720p"
            s.contains("480p") || s.contains("576p") -> "SD"
            else -> "?"
        }
    }

    fun isCam(t: String): Boolean =
        Regex("\\b(cam|camrip|hdcam|ts|telesync|hdts|tc|telecine|scr|screener)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)

    fun isVietnamese(t: String): Boolean =
        Regex("vietsub|viet\\s?sub|thuyết minh|thuyet minh|vie\\b|vietnamese|lồng tiếng", RegexOption.IGNORE_CASE).containsMatchIn(t)

    fun episodeRegex(season: Int, episode: Int) = Regex(
        "s0?${season}[ ._-]?e0?${episode}(?!\\d)|\\b${season}x0?${episode}(?!\\d)",
        RegexOption.IGNORE_CASE,
    )

    fun seasonPackRegex(season: Int) = Regex(
        "s0?${season}(?![0-9e])|season[ ._-]?0?${season}(?!\\d)|mùa[ ._-]?0?${season}(?!\\d)",
        RegexOption.IGNORE_CASE,
    )

    fun hasAnyEpisode(t: String) = Regex("s\\d{1,2}[ ._-]?e\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(t)

    /** Nhãn ngắn hiển thị cạnh nguồn: HDR, DV, HEVC, âm thanh. */
    fun tags(t: String): List<String> {
        val s = t.lowercase()
        val tags = mutableListOf<String>()
        if (Regex("\\bdv\\b|dolby.?vision|dovi").containsMatchIn(s)) tags += "Dolby Vision"
        else if (s.contains("hdr")) tags += "HDR"
        if (Regex("x265|h\\.?265|hevc").containsMatchIn(s)) tags += "HEVC"
        if (s.contains("remux")) tags += "REMUX"
        if (Regex("atmos").containsMatchIn(s)) tags += "Atmos"
        else if (Regex("truehd").containsMatchIn(s)) tags += "TrueHD"
        else if (Regex("\\bdts").containsMatchIn(s)) tags += "DTS"
        else if (Regex("ddp|dd\\+|eac3|e-ac-3").containsMatchIn(s)) tags += "DD+"
        if (isVietnamese(t)) tags += "Vietsub"
        return tags
    }
}
