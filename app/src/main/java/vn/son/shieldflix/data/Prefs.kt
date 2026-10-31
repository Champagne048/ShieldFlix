package vn.son.shieldflix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Lưu cài đặt và tiến độ xem (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("shieldflix", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Tăng mỗi khi cài đặt đổi, để giao diện tự tải lại. */
    val version: StateFlow<Int> = _version

    private fun str(key: String, def: String = "") = sp.getString(key, def) ?: def
    private fun put(key: String, value: String) {
        sp.edit().putString(key, value.trim()).apply()
        _version.value = _version.value + 1
    }

    var tmdbKey: String
        get() = str("tmdb_key")
        set(v) = put("tmdb_key", v)

    var osApiKey: String
        get() = str("os_api_key")
        set(v) = put("os_api_key", v)

    var osUser: String
        get() = str("os_user")
        set(v) = put("os_user", v)

    var osPass: String
        get() = str("os_pass")
        set(v) = put("os_pass", v)

    var subdlKey: String
        get() = str("subdl_key")
        set(v) = put("subdl_key", v)

    /** Mỗi dòng một URL Torznab đầy đủ (kèm apikey). */
    var torznabUrls: List<String>
        get() = str("torznab_urls").lines().map { it.trim() }.filter { it.startsWith("http") }
        set(v) = put("torznab_urls", v.joinToString("\n"))

    /** 2160p, 1080p hoặc 720p. */
    var preferredQuality: String
        get() = str("quality", "1080p")
        set(v) = put("quality", v)

    var subtitleSizeSp: Int
        get() = sp.getInt("sub_size", 30)
        set(v) { sp.edit().putInt("sub_size", v.coerceIn(16, 60)).apply(); bump() }

    var subtitleBackground: Boolean
        get() = sp.getBoolean("sub_bg", false)
        set(v) { sp.edit().putBoolean("sub_bg", v).apply(); bump() }

    var deleteAfterWatch: Boolean
        get() = sp.getBoolean("delete_after", true)
        set(v) { sp.edit().putBoolean("delete_after", v).apply(); bump() }

    var hideCam: Boolean
        get() = sp.getBoolean("hide_cam", true)
        set(v) { sp.edit().putBoolean("hide_cam", v).apply(); bump() }

    /** Mã PIN 4 số để trang cấu hình qua LAN không bị người khác sửa. */
    val pin: String
        get() {
            var p = str("pin")
            if (p.length != 4) {
                p = Random.nextInt(1000, 9999).toString()
                sp.edit().putString("pin", p).apply()
            }
            return p
        }

    private fun bump() {
        _version.value = _version.value + 1
    }

    val hasTmdb get() = tmdbKey.isNotBlank()
    val hasSources get() = torznabUrls.isNotEmpty()
    val hasSubtitles get() = osApiKey.isNotBlank() || subdlKey.isNotBlank()

    // ---------------- Tiến độ xem ----------------

    fun progressList(): List<WatchProgress> {
        val arr = try { JSONArray(str("progress", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            try { WatchProgress.fromJson(arr.getJSONObject(i)) } catch (e: Exception) { null }
        }.sortedByDescending { it.updatedAt }
    }

    fun progressFor(key: String): WatchProgress? = progressList().firstOrNull { it.key == key }

    fun saveProgress(p: WatchProgress) {
        val list = progressList().filter { it.key != p.key }.toMutableList()
        // Xem gần hết (>= 92%) thì coi như xong, bỏ khỏi "Tiếp tục xem".
        val finished = p.durationMs > 0 && p.positionMs >= p.durationMs * 0.92
        if (!finished && p.positionMs > 30_000) list.add(0, p)
        val arr = JSONArray()
        list.take(30).forEach { arr.put(it.toJson()) }
        sp.edit().putString("progress", arr.toString()).apply()
        bump()
    }

    fun removeProgress(key: String) {
        val arr = JSONArray()
        progressList().filter { it.key != key }.forEach { arr.put(it.toJson()) }
        sp.edit().putString("progress", arr.toString()).apply()
        bump()
    }
}

data class WatchProgress(
    val key: String,
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val subtitle: String,
    val backdrop: String?,
    val poster: String?,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val magnet: String,
    val torrentName: String,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("tmdbId", tmdbId).put("type", type.name)
        .put("title", title).put("subtitle", subtitle)
        .put("backdrop", backdrop ?: "").put("poster", poster ?: "")
        .put("season", season).put("episode", episode)
        .put("pos", positionMs).put("dur", durationMs)
        .put("magnet", magnet).put("tname", torrentName).put("at", updatedAt)

    companion object {
        fun keyOf(type: MediaType, tmdbId: Int, season: Int = 0, episode: Int = 0) =
            if (type == MediaType.TV) "tv:$tmdbId:$season:$episode" else "movie:$tmdbId"

        fun fromJson(o: JSONObject) = WatchProgress(
            key = o.getString("key"),
            tmdbId = o.optInt("tmdbId"),
            type = MediaType.valueOf(o.optString("type", "MOVIE")),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            backdrop = o.optString("backdrop").ifBlank { null },
            poster = o.optString("poster").ifBlank { null },
            season = o.optInt("season"),
            episode = o.optInt("episode"),
            positionMs = o.optLong("pos"),
            durationMs = o.optLong("dur"),
            magnet = o.optString("magnet"),
            torrentName = o.optString("tname"),
            updatedAt = o.optLong("at"),
        )
    }
}
