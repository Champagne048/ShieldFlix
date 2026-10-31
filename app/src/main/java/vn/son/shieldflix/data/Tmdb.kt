package vn.son.shieldflix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Lấy thông tin phim, poster, mô tả tiếng Việt từ TMDB. */
class Tmdb(private val prefs: Prefs) {

    private val base = "https://api.themoviedb.org/3"
    private val lang = "vi-VN"

    private fun isBearer(key: String) = key.length > 40

    private suspend fun call(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val key = prefs.tmdbKey
        if (key.isBlank()) throw IllegalStateException("Chưa nhập khoá TMDB")
        val all = params.toMutableMap()
        if (!all.containsKey("language")) all["language"] = lang
        if (!isBearer(key)) all["api_key"] = key
        val query = all.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val headers = if (isBearer(key)) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return JSONObject(Net.get("$base$path?$query", headers))
    }

    private fun parseList(arr: JSONArray?, forced: MediaType? = null): List<Title> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            parseTitle(o, forced)
        }.filter { it.poster != null }
    }

    private fun parseTitle(o: JSONObject, forced: MediaType?): Title? {
        val type = forced ?: when (o.optString("media_type")) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV
            else -> return null
        }
        val name = if (type == MediaType.MOVIE) o.optString("title") else o.optString("name")
        val original = if (type == MediaType.MOVIE) o.optString("original_title") else o.optString("original_name")
        val date = if (type == MediaType.MOVIE) o.optString("release_date") else o.optString("first_air_date")
        return Title(
            id = o.optInt("id"),
            type = type,
            name = name.ifBlank { original },
            originalName = original.ifBlank { name },
            overview = o.optString("overview"),
            poster = o.optString("poster_path").takeIf { it.startsWith("/") },
            backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") },
            year = date.take(4),
            rating = o.optDouble("vote_average", 0.0),
        )
    }

    /** Các hàng trên trang chủ, gọi song song. */
    suspend fun homeRows(): List<TitleRow> = coroutineScope {
        data class Spec(val label: String, val path: String, val params: Map<String, String>, val type: MediaType?)
        val specs = listOf(
            Spec("Thịnh hành tuần này", "/trending/all/week", emptyMap(), null),
            Spec("Phim lẻ phổ biến", "/movie/popular", emptyMap(), MediaType.MOVIE),
            Spec("Series phổ biến", "/tv/popular", emptyMap(), MediaType.TV),
            Spec("Đang chiếu rạp", "/movie/now_playing", emptyMap(), MediaType.MOVIE),
            Spec("Đánh giá cao", "/movie/top_rated", emptyMap(), MediaType.MOVIE),
            Spec("Phim Hàn", "/discover/tv", mapOf("with_original_language" to "ko", "sort_by" to "popularity.desc"), MediaType.TV),
            Spec("Hành động", "/discover/movie", mapOf("with_genres" to "28", "sort_by" to "popularity.desc"), MediaType.MOVIE),
            Spec("Hài", "/discover/movie", mapOf("with_genres" to "35", "sort_by" to "popularity.desc"), MediaType.MOVIE),
            Spec("Kinh dị", "/discover/movie", mapOf("with_genres" to "27", "sort_by" to "popularity.desc"), MediaType.MOVIE),
            Spec("Khoa học viễn tưởng", "/discover/movie", mapOf("with_genres" to "878", "sort_by" to "popularity.desc"), MediaType.MOVIE),
            Spec("Hoạt hình", "/discover/movie", mapOf("with_genres" to "16", "sort_by" to "popularity.desc"), MediaType.MOVIE),
            Spec("Phim Việt", "/discover/movie", mapOf("with_original_language" to "vi", "sort_by" to "popularity.desc"), MediaType.MOVIE),
        )
        specs.map { s ->
            async {
                try {
                    TitleRow(s.label, parseList(call(s.path, s.params).optJSONArray("results"), s.type))
                } catch (e: Exception) {
                    if (s.path.startsWith("/trending")) throw e // lỗi khoá thì báo luôn
                    TitleRow(s.label, emptyList())
                }
            }
        }.awaitAll().filter { it.items.isNotEmpty() }.let { rows ->
            val trending = rows.firstOrNull() ?: return@let rows
            listOf(TitleRow("Top 10 tuần này", trending.items.take(10), RowStyle.TOP10)) + rows
        }
    }

    suspend fun search(query: String): List<Title> {
        if (query.isBlank()) return emptyList()
        val o = call("/search/multi", mapOf("query" to query, "include_adult" to "false"))
        return parseList(o.optJSONArray("results"))
    }

    suspend fun detail(type: MediaType, id: Int): TitleDetail {
        val append = if (type == MediaType.MOVIE) "external_ids,credits,release_dates" else "external_ids,credits,content_ratings"
        val o = call("/${type.api}/$id", mapOf("append_to_response" to append))
        var title = parseTitle(o, type) ?: throw IllegalStateException("Không đọc được phim")
        if (title.overview.isBlank()) {
            // Chưa có mô tả tiếng Việt thì lấy tiếng Anh.
            try {
                val en = call("/${type.api}/$id", mapOf("language" to "en-US"))
                title = title.copy(overview = en.optString("overview"))
            } catch (_: Exception) {
            }
        }
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
        val cast = o.optJSONObject("credits")?.optJSONArray("cast")?.let { a ->
            (0 until minOf(a.length(), 6)).map { a.getJSONObject(it).optString("name") }
        } ?: emptyList()
        val imdb = o.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.startsWith("tt") }
            ?: o.optString("imdb_id").takeIf { it.startsWith("tt") }
        val runtime = if (type == MediaType.MOVIE) o.optInt("runtime").takeIf { it > 0 }
        else o.optJSONArray("episode_run_time")?.let { if (it.length() > 0) it.optInt(0) else null }
        title = title.copy(imdbId = imdb, logo = imdb?.let { Cinemeta.logoUrl(it) }, genres = genres)
        val seasons = o.optJSONArray("seasons")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optInt("season_number") > 0 && it.optInt("episode_count") > 0 }
                .map { SeasonInfo(it.optInt("season_number"), it.optString("name"), it.optInt("episode_count")) }
        } ?: emptyList()
        return TitleDetail(
            title = title,
            imdbId = imdb,
            runtimeMin = runtime,
            genres = genres,
            cast = cast,
            tagline = o.optString("tagline").ifBlank { null },
            seasons = seasons,
            certification = certification(o, type),
        )
    }

    private fun certification(o: JSONObject, type: MediaType): String? {
        return try {
            if (type == MediaType.MOVIE) {
                val res = o.optJSONObject("release_dates")?.optJSONArray("results") ?: return null
                for (i in 0 until res.length()) {
                    val r = res.getJSONObject(i)
                    if (r.optString("iso_3166_1") == "US") {
                        val d = r.optJSONArray("release_dates") ?: continue
                        for (j in 0 until d.length()) {
                            val c = d.getJSONObject(j).optString("certification")
                            if (c.isNotBlank()) return c
                        }
                    }
                }
                null
            } else {
                val res = o.optJSONObject("content_ratings")?.optJSONArray("results") ?: return null
                (0 until res.length()).map { res.getJSONObject(it) }
                    .firstOrNull { it.optString("iso_3166_1") == "US" }?.optString("rating")?.ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Logo tên phim (ưu tiên tiếng Việt, rồi tiếng Anh, rồi không chữ). */
    suspend fun logo(type: MediaType, id: Int): String? {
        val o = call("/${type.api}/$id/images", mapOf("include_image_language" to "vi,en,null", "language" to "vi-VN"))
        val arr = o.optJSONArray("logos") ?: return null
        val logos = (0 until arr.length()).map { arr.getJSONObject(it) }
        val best = listOf("vi", "en", "").firstNotNullOfOrNull { lang ->
            logos.firstOrNull { it.optString("iso_639_1").let { l -> if (lang.isEmpty()) l.isEmpty() || l == "null" else l == lang } }
        } ?: logos.firstOrNull()
        return best?.optString("file_path")?.takeIf { it.startsWith("/") }?.let { img(it, "w500") }
    }

    suspend fun season(tvId: Int, season: Int): List<Episode> {
        val o = call("/tv/$tvId/season/$season")
        val arr = o.optJSONArray("episodes") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Episode(
                season = season,
                number = it.optInt("episode_number"),
                name = it.optString("name"),
                overview = it.optString("overview"),
                still = it.optString("still_path").takeIf { p -> p.startsWith("/") },
                runtime = it.optInt("runtime").takeIf { r -> r > 0 },
            )
        }
    }

    companion object {
        /** Đường dẫn TMDB ("/abc.jpg") thành URL ảnh; URL đầy đủ (Cinemeta) giữ nguyên. */
        fun img(path: String?, size: String = "w500"): String? = when {
            path.isNullOrBlank() -> null
            path.startsWith("http") -> path
            else -> "https://image.tmdb.org/t/p/$size$path"
        }
    }
}
