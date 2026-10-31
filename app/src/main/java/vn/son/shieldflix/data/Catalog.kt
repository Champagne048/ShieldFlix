package vn.son.shieldflix.data

/**
 * Nguồn thông tin phim cho giao diện: có khoá TMDB thì dùng TMDB (tên, mô tả tiếng Việt),
 * chưa có thì dùng Cinemeta (không cần khoá) để app luôn có trang chủ đầy đủ ngay khi mở.
 */
class Catalog(private val prefs: Prefs, private val tmdb: Tmdb, private val cinemeta: Cinemeta) {

    private val logoCache = HashMap<String, String?>()

    /** Khoá phân biệt dữ liệu trang chủ theo nguồn đang dùng. */
    val sourceKey get() = if (prefs.hasTmdb) "tmdb:${prefs.tmdbKey}" else "cinemeta"

    suspend fun homeRows(): List<TitleRow> {
        if (prefs.hasTmdb) {
            try {
                return tmdb.homeRows()
            } catch (_: Exception) {
                // Khoá sai hoặc TMDB lỗi: vẫn hiện phim từ Cinemeta.
            }
        }
        return cinemeta.homeRows()
    }

    suspend fun search(query: String): List<Title> {
        if (prefs.hasTmdb) {
            try {
                return tmdb.search(query)
            } catch (_: Exception) {
            }
        }
        return cinemeta.search(query)
    }

    suspend fun detail(type: MediaType, tmdbId: Int, imdbId: String?): TitleDetail {
        if (prefs.hasTmdb && tmdbId > 0) {
            try {
                return tmdb.detail(type, tmdbId)
            } catch (e: Exception) {
                if (imdbId == null) throw e
            }
        }
        if (imdbId != null) return cinemeta.detail(type, imdbId)
        throw IllegalStateException("Cần khoá TMDB để mở phim này")
    }

    suspend fun episodes(d: TitleDetail, season: Int): List<Episode> {
        d.allEpisodes?.let { all -> return all.filter { it.season == season } }
        return tmdb.season(d.title.id, season)
    }

    /** Logo tên phim cho hero; null nếu không có (khi đó hiện chữ). */
    suspend fun logoFor(t: Title): String? {
        t.logo?.let { return it }
        t.imdbId?.let { return Cinemeta.logoUrl(it) }
        if (!prefs.hasTmdb || t.id <= 0) return null
        val key = "${t.type}:${t.id}"
        if (logoCache.containsKey(key)) return logoCache[key]
        val url = try { tmdb.logo(t.type, t.id) } catch (_: Exception) { null }
        logoCache[key] = url
        return url
    }
}
