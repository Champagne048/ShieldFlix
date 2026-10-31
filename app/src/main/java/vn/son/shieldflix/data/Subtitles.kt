package vn.son.shieldflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

data class SubtitleOption(
    val provider: String,
    val id: String,
    val name: String,
    val downloads: Int,
    val hearingImpaired: Boolean,
    /** Subdl: đường dẫn zip. Torrent: chỉ số file trong torrent. */
    val extra: String? = null,
    val match: Double = 0.0,
)

/** Tìm và tải phụ đề tiếng Việt, chuẩn hoá về UTF-8. */
class SubtitleService(private val prefs: Prefs, cacheDir: File) {

    private val dir = File(cacheDir, "subs").apply { mkdirs() }
    private var osToken: String? = null

    // ---------------- Tìm ----------------

    suspend fun search(
        type: MediaType, tmdbId: Int, imdbId: String?, season: Int, episode: Int, releaseName: String,
    ): List<SubtitleOption> = coroutineScope {
        val os = async { try { searchOpenSubtitles(type, tmdbId, imdbId, season, episode) } catch (e: Exception) { emptyList() } }
        val sd = async { try { searchSubdl(type, tmdbId, imdbId, season, episode) } catch (e: Exception) { emptyList() } }
        (os.await() + sd.await())
            .map { it.copy(match = similarity(it.name, releaseName)) }
            .sortedWith(compareByDescending<SubtitleOption> { it.match }.thenByDescending { it.downloads })
    }

    private fun osHeaders(): Map<String, String> {
        val h = mutableMapOf("Api-Key" to prefs.osApiKey, "Accept" to "application/json")
        osToken?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    private suspend fun searchOpenSubtitles(
        type: MediaType, tmdbId: Int, imdbId: String?, season: Int, episode: Int,
    ): List<SubtitleOption> {
        if (prefs.osApiKey.isBlank()) return emptyList()
        // OpenSubtitles yêu cầu tham số xếp theo bảng chữ cái, chữ thường.
        val params = sortedMapOf("languages" to "vi")
        if (type == MediaType.TV && season > 0) {
            params["parent_tmdb_id"] = "$tmdbId"
            params["season_number"] = "$season"
            params["episode_number"] = "$episode"
        } else if (imdbId != null) {
            params["imdb_id"] = imdbId.removePrefix("tt").trimStart('0')
        } else {
            params["tmdb_id"] = "$tmdbId"
        }
        val qs = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val o = JSONObject(Net.get("https://api.opensubtitles.com/api/v1/subtitles?$qs", osHeaders()))
        val data = o.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val a = data.getJSONObject(i).optJSONObject("attributes") ?: return@mapNotNull null
            val file = a.optJSONArray("files")?.optJSONObject(0) ?: return@mapNotNull null
            SubtitleOption(
                provider = "OpenSubtitles",
                id = file.optLong("file_id").toString(),
                name = a.optString("release").ifBlank { file.optString("file_name") },
                downloads = a.optInt("download_count"),
                hearingImpaired = a.optBoolean("hearing_impaired"),
            )
        }
    }

    private suspend fun searchSubdl(
        type: MediaType, tmdbId: Int, imdbId: String?, season: Int, episode: Int,
    ): List<SubtitleOption> {
        if (prefs.subdlKey.isBlank()) return emptyList()
        val sb = StringBuilder("https://api.subdl.com/api/v1/subtitles?api_key=${prefs.subdlKey}&languages=VI&subs_per_page=30")
        sb.append("&tmdb_id=$tmdbId&type=${if (type == MediaType.TV) "tv" else "movie"}")
        if (type == MediaType.TV && season > 0) sb.append("&season_number=$season&episode_number=$episode")
        val o = JSONObject(Net.get(sb.toString()))
        if (!o.optBoolean("status", true)) return emptyList()
        val subs = o.optJSONArray("subtitles") ?: return emptyList()
        return (0 until subs.length()).map { subs.getJSONObject(it) }.map {
            SubtitleOption(
                provider = "Subdl",
                id = it.optString("url"),
                name = it.optString("release_name").ifBlank { it.optString("name") },
                downloads = 0,
                hearingImpaired = it.optBoolean("hi"),
                extra = it.optString("url"),
            )
        }.filter { it.extra?.isNotBlank() == true }
    }

    // ---------------- Tải ----------------

    /** Tải phụ đề về file UTF-8. Trả về file .srt/.ass/.vtt. */
    suspend fun download(opt: SubtitleOption, season: Int, episode: Int): File {
        val raw: Pair<String, ByteArray> = when (opt.provider) {
            "OpenSubtitles" -> downloadOs(opt)
            "Subdl" -> downloadSubdl(opt, season, episode)
            else -> throw IOException("Nguồn phụ đề không hỗ trợ")
        }
        return saveNormalized(raw.first, raw.second, opt.provider + "_" + opt.id.hashCode())
    }

    private suspend fun osLogin() {
        if (osToken != null || prefs.osUser.isBlank() || prefs.osPass.isBlank()) return
        try {
            val body = JSONObject().put("username", prefs.osUser).put("password", prefs.osPass).toString()
            val o = JSONObject(Net.postJson("https://api.opensubtitles.com/api/v1/login", body, osHeaders()))
            osToken = o.optString("token").ifBlank { null }
        } catch (_: Exception) {
        }
    }

    private suspend fun downloadOs(opt: SubtitleOption): Pair<String, ByteArray> {
        osLogin()
        val body = JSONObject().put("file_id", opt.id.toLong()).put("sub_format", "srt").toString()
        val o = JSONObject(Net.postJson("https://api.opensubtitles.com/api/v1/download", body, osHeaders()))
        val link = o.optString("link")
        if (link.isBlank()) throw IOException(o.optString("message", "OpenSubtitles hết lượt tải hôm nay"))
        val name = o.optString("file_name", "sub.srt")
        return name to Net.bytes(link)
    }

    private suspend fun downloadSubdl(opt: SubtitleOption, season: Int, episode: Int): Pair<String, ByteArray> {
        val path = opt.extra ?: throw IOException("Thiếu link Subdl")
        val zip = Net.bytes("https://dl.subdl.com$path")
        return withContext(Dispatchers.IO) { pickFromZip(zip, season, episode) }
    }

    /** Chọn file phụ đề trong zip; với series chọn đúng tập. */
    fun pickFromZip(zip: ByteArray, season: Int, episode: Int): Pair<String, ByteArray> {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val n = e.name
                if (!e.isDirectory && isSubFile(n)) entries += n to zis.readBytes()
                e = zis.nextEntry
            }
        }
        if (entries.isEmpty()) throw IOException("File zip không có phụ đề")
        if (season > 0 && entries.size > 1) {
            val re = Quality.episodeRegex(season, episode)
            entries.firstOrNull { re.containsMatchIn(it.first) }?.let { return it }
        }
        return entries.firstOrNull { it.first.endsWith(".srt", true) } ?: entries.first()
    }

    fun isSubFile(name: String) = name.endsWith(".srt", true) || name.endsWith(".ass", true) ||
        name.endsWith(".ssa", true) || name.endsWith(".vtt", true)

    /** Lưu phụ đề sau khi chuyển về UTF-8 (phụ đề Việt hay ở dạng Windows-1258/UTF-16). */
    fun saveNormalized(fileName: String, bytes: ByteArray, id: String): File {
        val ext = fileName.substringAfterLast('.', "srt").lowercase().let { if (it == "ssa") "ass" else it }
        val text = decode(bytes)
        val f = File(dir, "$id.$ext")
        f.writeText(text, Charsets.UTF_8)
        return f
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        // Thử UTF-8 nghiêm ngặt trước
        try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
        }
        for (cs in listOf("windows-1258", "windows-1252")) {
            try {
                if (Charset.isSupported(cs)) return String(bytes, Charset.forName(cs))
            } catch (_: Exception) {
            }
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    /** Tạo bản phụ đề lệch thời gian [offsetMs] (dương = chữ hiện muộn hơn). */
    fun shifted(original: File, offsetMs: Long): File {
        if (offsetMs == 0L) return original
        val text = original.readText(Charsets.UTF_8)
        val out = if (original.extension.equals("ass", true)) shiftAss(text, offsetMs) else shiftSrtVtt(text, offsetMs)
        val f = File(dir, original.nameWithoutExtension + "_" + offsetMs + "." + original.extension)
        f.writeText(out, Charsets.UTF_8)
        return f
    }

    private fun shiftSrtVtt(text: String, offset: Long): String {
        val re = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})([,.])(\\d{3})")
        return re.replace(text) { m ->
            val (h, mi, s, sep, ms) = m.destructured
            val t = maxOf(0L, h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + ms.toLong() + offset)
            "%02d:%02d:%02d%s%03d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, sep, t % 1000)
        }
    }

    private fun shiftAss(text: String, offset: Long): String {
        val re = Regex("(\\d):(\\d{2}):(\\d{2})\\.(\\d{2})")
        return text.lines().joinToString("\n") { line ->
            if (!line.startsWith("Dialogue:")) line
            else re.replace(line) { m ->
                val (h, mi, s, cs) = m.destructured
                val t = maxOf(0L, h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + cs.toLong() * 10 + offset)
                "%d:%02d:%02d.%02d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, (t % 1000) / 10)
            }
        }
    }

    companion object {
        /** Độ giống giữa tên bản phụ đề và tên torrent (0..1), để chọn phụ đề khớp khung hình. */
        fun similarity(a: String, b: String): Double {
            fun tokens(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()
            val ta = tokens(a)
            val tb = tokens(b)
            if (ta.isEmpty() || tb.isEmpty()) return 0.0
            val inter = ta.intersect(tb).size.toDouble()
            var score = inter / ta.union(tb).size
            val keys = listOf("bluray", "web", "webrip", "webdl", "dl", "hdtv", "remux", "2160p", "1080p", "720p")
            keys.forEach { k -> if (k in ta && k in tb) score += 0.05 }
            // Tên nhóm phát hành thường đứng cuối.
            val groupA = a.lowercase().substringAfterLast('-').trim()
            val groupB = b.lowercase().substringAfterLast('-').substringBefore('.').trim()
            if (groupA.length in 2..20 && groupA.startsWith(groupB.take(groupA.length).ifBlank { "#" })) score += 0.2
            return score.coerceAtMost(1.0)
        }

        fun mimeFor(file: File): String = when (file.extension.lowercase()) {
            "ass", "ssa" -> "text/x-ssa"
            "vtt" -> "text/vtt"
            else -> "application/x-subrip"
        }
    }
}
