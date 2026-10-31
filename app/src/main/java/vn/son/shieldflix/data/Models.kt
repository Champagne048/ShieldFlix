package vn.son.shieldflix.data

enum class MediaType(val api: String) { MOVIE("movie"), TV("tv") }

/** Một phim / series hiển thị trên lưới. */
data class Title(
    val id: Int,
    val type: MediaType,
    val name: String,
    val originalName: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val year: String,
    val rating: Double,
    val imdbId: String? = null,
    /** Ảnh logo tên phim (nền trong suốt), giống Netflix. */
    val logo: String? = null,
    val genres: List<String> = emptyList(),
) {
    val key get() = "$type:$id:${imdbId ?: ""}"
}

enum class RowStyle { TOP10, LANDSCAPE }

data class TitleRow(val title: String, val items: List<Title>, val style: RowStyle = RowStyle.LANDSCAPE)

data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int)

data class Episode(
    val season: Int,
    val number: Int,
    val name: String,
    val overview: String,
    val still: String?,
    val runtime: Int?,
)

data class TitleDetail(
    val title: Title,
    val imdbId: String?,
    val runtimeMin: Int?,
    val genres: List<String>,
    val cast: List<String>,
    val tagline: String?,
    val seasons: List<SeasonInfo>,
    val certification: String?,
    /** Danh sách tập lấy sẵn (nguồn Cinemeta); null thì tải theo mùa từ TMDB. */
    val allEpisodes: List<Episode>? = null,
)

/** Một kết quả torrent từ nguồn Torznab. */
data class TorrentResult(
    val title: String,
    val sizeBytes: Long,
    val seeders: Int,
    val peers: Int,
    val magnet: String?,
    val link: String?,
    val infoHash: String?,
    val indexer: String,
    val quality: String,
    val score: Double,
    val vietsub: Boolean,
)

/** Nguồn để mở torrent: magnet hoặc nội dung file .torrent. */
sealed class TorrentSource {
    data class Magnet(val uri: String) : TorrentSource()
    class FileBytes(val bytes: ByteArray, val magnetFallback: String?) : TorrentSource()
}

/** Thông tin để màn hình phát biết phải phát gì. */
data class PlayRequest(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val originalTitle: String,
    val year: String,
    val imdbId: String?,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeName: String = "",
    val backdrop: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    /** Nguồn đã chọn sẵn; null thì tự tìm nguồn nhiều seed nhất. */
    val result: TorrentResult? = null,
    /** Magnet trực tiếp (dán từ điện thoại, mở từ app khác, hoặc tiếp tục xem). */
    val magnet: String? = null,
    val startPositionMs: Long = 0,
) {
    val progressKey get() = WatchProgress.keyOf(type, tmdbId, season, episode)
    val displayTitle get() =
        if (type == MediaType.TV && season > 0) "$title · S%02dE%02d".format(season, episode) else title
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "?"
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1_048_576.0)
}

fun formatSpeed(bps: Long): String {
    val mb = bps / 1_048_576.0
    return if (mb >= 1) "%.1f MB/s".format(mb) else "%.0f KB/s".format(bps / 1024.0)
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
