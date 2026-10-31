package vn.son.shieldflix

import android.app.Application
import kotlinx.coroutines.channels.Channel
import vn.son.shieldflix.data.Catalog
import vn.son.shieldflix.data.Cinemeta
import vn.son.shieldflix.data.Prefs
import vn.son.shieldflix.data.SubtitleService
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.TorznabClient
import vn.son.shieldflix.torrent.StreamServer
import vn.son.shieldflix.torrent.TorrentEngine
import vn.son.shieldflix.web.ConfigServer

/** Giữ các đối tượng dùng chung cho toàn app. */
class App : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var tmdb: Tmdb
        private set
    lateinit var catalog: Catalog
        private set
    lateinit var torznab: TorznabClient
        private set
    lateinit var subtitles: SubtitleService
        private set
    lateinit var engine: TorrentEngine
        private set
    lateinit var streamServer: StreamServer
        private set
    var configServer: ConfigServer? = null
        private set

    /** Magnet gửi tới từ trang cấu hình trên điện thoại hoặc từ intent. */
    val incomingMagnets = Channel<String>(Channel.BUFFERED)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        tmdb = Tmdb(prefs)
        catalog = Catalog(prefs, tmdb, Cinemeta())
        torznab = TorznabClient(prefs)
        subtitles = SubtitleService(prefs, cacheDir)
        engine = TorrentEngine(this)
        streamServer = StreamServer()
        val config = ConfigServer(this, ConfigServer.PORT)
        configServer = config
        // Mở cổng ngoài luồng giao diện.
        Thread {
            try { streamServer.startServer() } catch (_: Exception) {}
            try { config.startServer() } catch (_: Exception) { configServer = null }
        }.start()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
