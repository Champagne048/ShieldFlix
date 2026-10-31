package vn.son.shieldflix.web

import fi.iki.elonen.NanoHTTPD
import vn.son.shieldflix.App
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Trang cấu hình mở từ điện thoại/máy tính trong cùng mạng: http://<IP Shield>:8090
 * Nhập khoá API, nguồn Torznab, và dán magnet để phát ngay trên TV.
 */
class ConfigServer(private val app: App, port: Int) : NanoHTTPD(port) {

    fun startServer() = start(SOCKET_READ_TIMEOUT, true)

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.POST && session.uri == "/save" -> save(session)
                session.method == Method.POST && session.uri == "/play" -> play(session)
                else -> html(page(null))
            }
        } catch (e: Exception) {
            html(page("Lỗi: ${e.message}"))
        }
    }

    @Suppress("DEPRECATION")
    private fun form(session: IHTTPSession): Map<String, String> {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return session.parms ?: emptyMap()
    }

    private fun save(session: IHTTPSession): Response {
        val f = form(session)
        if (f["pin"]?.trim() != app.prefs.pin) return html(page("Sai mã PIN. Xem mã PIN trên màn hình Cài đặt của TV."))
        val p = app.prefs
        f["tmdb"]?.let { p.tmdbKey = it }
        f["os_key"]?.let { p.osApiKey = it }
        f["os_user"]?.let { p.osUser = it }
        f["os_pass"]?.let { if (it.isNotBlank()) p.osPass = it }
        f["subdl"]?.let { p.subdlKey = it }
        f["torznab"]?.let { p.torznabUrls = it.lines() }
        f["quality"]?.let { if (it in listOf("2160p", "1080p", "720p")) p.preferredQuality = it }
        return html(page("Đã lưu. TV sẽ tự tải lại."))
    }

    private fun play(session: IHTTPSession): Response {
        val f = form(session)
        if (f["pin"]?.trim() != app.prefs.pin) return html(page("Sai mã PIN."))
        val m = f["magnet"]?.trim().orEmpty()
        if (!m.startsWith("magnet:")) return html(page("Magnet không hợp lệ (phải bắt đầu bằng magnet:)."))
        app.incomingMagnets.trySend(m)
        return html(page("Đã gửi sang TV, đang mở…"))
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(message: String?): String {
        val p = app.prefs
        fun sel(q: String) = if (p.preferredQuality == q) "selected" else ""
        return """
<!doctype html><html lang="vi"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ShieldFlix – Cài đặt</title>
<style>
:root{color-scheme:dark}
body{margin:0;background:#141414;color:#eee;font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:20px 16px 60px}
h1{color:#e50914;font-size:28px;margin:0 0 4px;letter-spacing:.5px}
h2{font-size:17px;margin:28px 0 8px;color:#fff}
p.hint{color:#aaa;margin:2px 0 10px;font-size:13px}
label{display:block;margin:12px 0 4px;color:#ccc;font-size:14px}
input,textarea,select{width:100%;box-sizing:border-box;background:#222;border:1px solid #444;color:#fff;border-radius:6px;padding:10px;font:inherit}
textarea{min-height:90px;font-family:ui-monospace,Menlo,monospace;font-size:13px}
button{margin-top:16px;background:#e50914;color:#fff;border:0;border-radius:6px;padding:12px 22px;font-weight:600;font-size:15px}
.msg{background:#1f3a1f;border:1px solid #2e6b2e;padding:10px 12px;border-radius:6px;margin:14px 0}
.card{background:#1b1b1b;border:1px solid #2a2a2a;border-radius:10px;padding:4px 16px 18px;margin-top:18px}
a{color:#ff6b6b}
</style></head><body><main>
<h1>SHIELDFLIX</h1>
<p class="hint">Trang cấu hình cho app trên Shield. Mã PIN hiện trên màn hình Cài đặt của TV.</p>
${if (message != null) "<div class=msg>${esc(message)}</div>" else ""}

<form method="post" action="/play" class="card">
<h2>Phát magnet trên TV</h2>
<label>Magnet link</label><textarea name="magnet" placeholder="magnet:?xt=urn:btih:..."></textarea>
<label>Mã PIN</label><input name="pin" inputmode="numeric" maxlength="4">
<button>Phát trên TV</button>
</form>

<form method="post" action="/save" class="card">
<h2>Thông tin phim (bắt buộc)</h2>
<p class="hint">Khoá TMDB miễn phí: đăng ký tại <a href="https://www.themoviedb.org/settings/api">themoviedb.org/settings/api</a>. Dán API Key (v3) hoặc Read Access Token.</p>
<label>TMDB API key / token</label><input name="tmdb" value="${esc(p.tmdbKey)}">

<h2>Nguồn torrent (Torznab)</h2>
<p class="hint">Mỗi dòng một URL Torznab đầy đủ, kèm apikey. Ví dụ Jackett chạy trên máy tính trong nhà:<br>
<code>http://192.168.1.10:9117/api/v2.0/indexers/all/results/torznab/api?apikey=XXXX</code><br>
Prowlarr: <code>http://192.168.1.10:9696/1/api?apikey=XXXX</code> (mỗi indexer một dòng).</p>
<textarea name="torznab">${esc(p.torznabUrls.joinToString("\n"))}</textarea>
<label>Chất lượng ưu tiên</label>
<select name="quality"><option ${sel("2160p")}>2160p</option><option ${sel("1080p")}>1080p</option><option ${sel("720p")}>720p</option></select>

<h2>Phụ đề tiếng Việt</h2>
<p class="hint">Nên có ít nhất một nguồn. Subdl: lấy key tại <a href="https://subdl.com/panel/api">subdl.com/panel/api</a>.
OpenSubtitles: tạo API consumer tại <a href="https://www.opensubtitles.com/consumers">opensubtitles.com/consumers</a>; nhập thêm tài khoản để được nhiều lượt tải/ngày hơn.</p>
<label>Subdl API key</label><input name="subdl" value="${esc(p.subdlKey)}">
<label>OpenSubtitles API key</label><input name="os_key" value="${esc(p.osApiKey)}">
<label>OpenSubtitles tài khoản</label><input name="os_user" value="${esc(p.osUser)}" autocomplete="username">
<label>OpenSubtitles mật khẩu ${if (p.osPass.isNotBlank()) "(đã lưu, để trống nếu không đổi)" else ""}</label><input name="os_pass" type="password" autocomplete="current-password">

<label>Mã PIN</label><input name="pin" inputmode="numeric" maxlength="4">
<button>Lưu cài đặt</button>
</form>
</main></body></html>
""".trimIndent()
    }

    companion object {
        const val PORT = 8090

        /** Địa chỉ IPv4 trong mạng LAN của Shield (Wi-Fi hoặc dây). */
        fun lanAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
