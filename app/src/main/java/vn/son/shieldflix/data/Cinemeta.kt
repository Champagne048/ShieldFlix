package vn.son.shieldflix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

/**
 * Danh mục phim công khai của Cinemeta (không cần khoá): poster, ảnh nền, logo tên phim, mô tả, danh sách tập.
 * Dùng khi chưa nhập khoá TMDB để trang chủ luôn có phim ngay khi mở app.
 */
class Cinemeta {

    private val base = "https://v3-cinemeta.strem.io"
    private val metaCache = HashMap<String, JSONObject>()

    private suspend fun json(path: String): JSONObject = JSONObject(Net.get("$base$path"))

    private fun MediaType.cm() = if (this == MediaType.TV) "series" else "movie"

    private fun parseItem(o: JSONObject, forced: MediaType? = null): Title? {
        val imdb = o.optString("imdb_id").ifBlank { o.optString("id") }.takeIf { it.startsWith("tt") } ?: return null
        val type = forced ?: if (o.optString("type") == "series") MediaType.TV else MediaType.MOVIE
        val poster = o.optString("poster").takeIf { it.startsWith("http") } ?: return null
        val name = o.optString("name").ifBlank { return null }
        val genres = (o.optJSONArray("genres") ?: o.optJSONArray("genre")).strings().map { genreVi(it) }
        val year = o.optString("releaseInfo").ifBlank { o.optString("year") }.take(4)
        return Title(
            id = o.optInt("moviedb_id", 0),
            type = type,
            name = name,
            originalName = name,
            overview = o.optString("description"),
            poster = poster,
            backdrop = o.optString("background").takeIf { it.startsWith("http") } ?: backgroundUrl(imdb),
            year = year,
            rating = o.optString("imdbRating").toDoubleOrNull() ?: 0.0,
            imdbId = imdb,
            logo = o.optString("logo").takeIf { it.startsWith("http") } ?: logoUrl(imdb),
            genres = genres,
        )
    }

    private suspend fun catalog(type: MediaType, id: String, extra: String? = null): List<Title> {
        val path = if (extra == null) "/catalog/${type.cm()}/$id.json" else "/catalog/${type.cm()}/$id/$extra.json"
        val arr = json(path).optJSONArray("metas") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> parseItem(o, type) } }
            .distinctBy { it.imdbId }
    }

    suspend fun homeRows(): List<TitleRow> = coroutineScope {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        data class Spec(val label: String, val type: MediaType, val id: String, val extra: String?)
        val specs = listOf(
            Spec("Phim lẻ thịnh hành", MediaType.MOVIE, "top", null),
            Spec("Series thịnh hành", MediaType.TV, "top", null),
            Spec("Phim mới ra mắt", MediaType.MOVIE, "year", "genre=$year"),
            Spec("Series mới", MediaType.TV, "year", "genre=$year"),
            Spec("Phim đánh giá cao", MediaType.MOVIE, "imdbRating", null),
            Spec("Series đỉnh cao", MediaType.TV, "imdbRating", null),
            Spec("Hành động", MediaType.MOVIE, "top", "genre=Action"),
            Spec("Hài hước", MediaType.MOVIE, "top", "genre=Comedy"),
            Spec("Giật gân", MediaType.MOVIE, "top", "genre=Thriller"),
            Spec("Khoa học viễn tưởng", MediaType.MOVIE, "top", "genre=Sci-Fi"),
            Spec("Kinh dị", MediaType.MOVIE, "top", "genre=Horror"),
            Spec("Hoạt hình", MediaType.MOVIE, "top", "genre=Animation"),
            Spec("Lãng mạn", MediaType.MOVIE, "top", "genre=Romance"),
            Spec("Hình sự", MediaType.TV, "top", "genre=Crime"),
            Spec("Phim tài liệu", MediaType.MOVIE, "top", "genre=Documentary"),
        )
        val rows = specs.map { s ->
            async {
                try {
                    TitleRow(s.label, catalog(s.type, s.id, s.extra))
                } catch (e: Exception) {
                    if (s.id == "top" && s.extra == null && s.type == MediaType.MOVIE) throw e
                    TitleRow(s.label, emptyList())
                }
            }
        }.awaitAll().filter { it.items.isNotEmpty() }
        val top = rows.firstOrNull()?.items?.take(10).orEmpty()
        if (top.isEmpty()) rows else listOf(TitleRow("Top 10 phim hôm nay", top, RowStyle.TOP10)) + rows
    }

    suspend fun search(query: String): List<Title> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val movies = async { try { catalog(MediaType.MOVIE, "top", "search=$q") } catch (e: Exception) { emptyList() } }
        val series = async { try { catalog(MediaType.TV, "top", "search=$q") } catch (e: Exception) { emptyList() } }
        // Xen kẽ phim lẻ và series để kết quả đầu có cả hai loại.
        val a = movies.await()
        val b = series.await()
        val out = ArrayList<Title>()
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        out
    }

    suspend fun detail(type: MediaType, imdbId: String): TitleDetail {
        val key = "${type.cm()}:$imdbId"
        val meta = metaCache[key] ?: json("/meta/${type.cm()}/$imdbId.json").optJSONObject("meta")
            ?.also { metaCache[key] = it } ?: throw IllegalStateException("Không tìm thấy thông tin phim")
        val title = parseItem(meta, type) ?: throw IllegalStateException("Không đọc được thông tin phim")
        val runtime = Regex("(\\d+)").find(meta.optString("runtime"))?.groupValues?.get(1)?.toIntOrNull()
        val episodes = if (type == MediaType.TV) parseVideos(meta.optJSONArray("videos")) else null
        val seasons = episodes.orEmpty().groupBy { it.season }.toSortedMap()
            .map { (s, eps) -> SeasonInfo(s, "Mùa $s", eps.size) }
        return TitleDetail(
            title = title,
            imdbId = imdbId,
            runtimeMin = runtime,
            genres = title.genres,
            cast = meta.optJSONArray("cast").strings().take(6),
            tagline = null,
            seasons = seasons,
            certification = null,
            allEpisodes = episodes,
        )
    }

    private fun parseVideos(arr: JSONArray?): List<Episode> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { v ->
            val season = v.optInt("season", 0)
            val ep = v.optInt("episode", v.optInt("number", 0))
            if (season <= 0 || ep <= 0) return@mapNotNull null
            Episode(
                season = season,
                number = ep,
                name = v.optString("name").ifBlank { v.optString("title") }.ifBlank { "Tập $ep" },
                overview = v.optString("overview").ifBlank { v.optString("description") },
                still = v.optString("thumbnail").takeIf { it.startsWith("http") },
                runtime = null,
            )
        }.distinctBy { it.season * 10000 + it.number }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    companion object {
        fun logoUrl(imdb: String) = "https://images.metahub.space/logo/medium/$imdb/img"
        fun backgroundUrl(imdb: String) = "https://images.metahub.space/background/medium/$imdb/img"

        private val genreMap = mapOf(
            "Action" to "Hành động", "Adventure" to "Phiêu lưu", "Animation" to "Hoạt hình",
            "Biography" to "Tiểu sử", "Comedy" to "Hài", "Crime" to "Hình sự", "Documentary" to "Tài liệu",
            "Drama" to "Chính kịch", "Family" to "Gia đình", "Fantasy" to "Giả tưởng", "History" to "Lịch sử",
            "Horror" to "Kinh dị", "Mystery" to "Bí ẩn", "Romance" to "Lãng mạn", "Sci-Fi" to "Viễn tưởng",
            "Sport" to "Thể thao", "Thriller" to "Giật gân", "War" to "Chiến tranh", "Western" to "Miền Tây",
            "Music" to "Âm nhạc", "Musical" to "Nhạc kịch", "Reality-TV" to "Truyền hình thực tế",
            "Talk-Show" to "Talk show", "Game-Show" to "Game show",
        )

        fun genreVi(g: String) = genreMap[g] ?: g
    }
}

internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).ifBlank { null } }
}
