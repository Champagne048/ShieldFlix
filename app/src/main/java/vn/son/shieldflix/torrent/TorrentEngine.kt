package vn.son.shieldflix.torrent

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import vn.son.shieldflix.data.Quality
import vn.son.shieldflix.data.TorrentSource
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

data class TorrentStats(
    val peers: Int,
    val seeds: Int,
    val downloadRate: Long,
    val progress: Float,
)

/** Thông tin torrent đã lấy xong metadata. */
class LoadedTorrent(val info: TorrentInfo, val bytes: ByteArray, val magnet: String) {
    val name: String get() = info.name()
    val hash: String get() = info.infoHash().toHex()
}

/**
 * Bọc libtorrent: lấy metadata từ magnet, tải ưu tiên theo vị trí đang xem,
 * cấp dữ liệu cho máy chủ stream. Mỗi lúc chỉ phát một torrent.
 */
class TorrentEngine(context: Context) {

    private val sm = SessionManager(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val baseDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "torrents").apply { mkdirs() }
    private val metaDir = File(context.cacheDir, "meta").apply { mkdirs() }

    @Volatile
    var active: ActiveTorrent? = null
        private set

    @Synchronized
    private fun ensureStarted() {
        if (!sm.isRunning) {
            val sp = SettingsPack()
                .connectionsLimit(400)
                .activeDownloads(4)
                .activeSeeds(4)
            sm.start(SessionParams(sp))
        }
    }

    /** Lấy metadata. Với magnet có thể mất 5–60 giây tuỳ số người chia sẻ. */
    suspend fun load(source: TorrentSource, onStatus: (String) -> Unit): LoadedTorrent = withContext(Dispatchers.IO) {
        ensureStarted()
        when (source) {
            is TorrentSource.FileBytes -> {
                val ti = TorrentInfo(source.bytes)
                val magnet = source.magnetFallback ?: magnetOf(ti)
                remember(ti, source.bytes)
                LoadedTorrent(ti, source.bytes, magnet)
            }
            is TorrentSource.Magnet -> {
                val hash = Regex("btih:([a-zA-Z0-9]+)").find(source.uri)?.groupValues?.get(1)?.lowercase()
                // Đã từng mở torrent này: dùng lại metadata, khỏi chờ.
                val cached = hash?.let { File(metaDir, "$it.torrent") }?.takeIf { it.length() > 0 }
                if (cached != null) {
                    val bytes = cached.readBytes()
                    return@withContext LoadedTorrent(TorrentInfo(bytes), bytes, source.uri)
                }
                onStatus("Đang kết nối mạng DHT…")
                var waited = 0
                while (sm.dhtNodes() < 10 && waited < 8000) {
                    coroutineContext.ensureActive()
                    delay(400); waited += 400
                }
                onStatus("Đang lấy thông tin torrent từ người chia sẻ…")
                val bytes = runInterruptible { sm.fetchMagnet(source.uri, 75, metaDir) }
                    ?: throw IOException("Không lấy được thông tin torrent sau 75 giây (ít người chia sẻ).")
                val ti = TorrentInfo(bytes)
                remember(ti, bytes)
                LoadedTorrent(ti, bytes, source.uri)
            }
        }
    }

    private fun remember(ti: TorrentInfo, bytes: ByteArray) {
        try { File(metaDir, ti.infoHash().toHex() + ".torrent").writeBytes(bytes) } catch (_: Exception) {}
    }

    private fun magnetOf(ti: TorrentInfo) =
        "magnet:?xt=urn:btih:${ti.infoHash().toHex()}&dn=${URLEncoder.encode(ti.name(), "UTF-8")}"

    // ---------------- Chọn file ----------------

    private val videoExt = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg")

    fun videoFiles(ti: TorrentInfo): List<Int> {
        val fs = ti.files()
        return (0 until fs.numFiles()).filter { i ->
            val n = fs.fileName(i).lowercase()
            n.substringAfterLast('.') in videoExt && !n.contains("sample") && fs.fileSize(i) > 30L * 1024 * 1024
        }
    }

    /** Chọn file video: đúng tập với series, file lớn nhất với phim lẻ. */
    fun pickFile(ti: TorrentInfo, season: Int, episode: Int): Int {
        val fs = ti.files()
        val videos = videoFiles(ti).ifEmpty { (0 until fs.numFiles()).toList() }
        if (season > 0) {
            val re = Quality.episodeRegex(season, episode)
            videos.filter { re.containsMatchIn(fs.filePath(it)) }.maxByOrNull { fs.fileSize(it) }?.let { return it }
            // Gói chỉ có số tập, vd "Show - 05.mkv"
            val epOnly = Regex("(?<![0-9])0?$episode(?![0-9p])")
            videos.filter { epOnly.containsMatchIn(fs.fileName(it).replace(Regex("(19|20)\\d{2}|\\d{3,4}p"), "")) }
                .takeIf { it.size == 1 }?.let { return it.first() }
        }
        return videos.maxBy { fs.fileSize(it) }
    }

    /** File phụ đề có sẵn trong torrent, ưu tiên tiếng Việt. */
    fun subtitleFiles(ti: TorrentInfo, videoIndex: Int): List<Pair<Int, String>> {
        val fs = ti.files()
        val videoBase = fs.fileName(videoIndex).substringBeforeLast('.').lowercase()
        return (0 until fs.numFiles())
            .filter { i ->
                val n = fs.fileName(i).lowercase()
                (n.endsWith(".srt") || n.endsWith(".ass") || n.endsWith(".ssa") || n.endsWith(".vtt")) && fs.fileSize(i) < 5_000_000
            }
            .map { it to fs.filePath(it) }
            .filter { (_, path) ->
                val p = path.lowercase()
                Regex("vie|viet|\\bvi\\b|vn\\b|tiếng việt").containsMatchIn(p) ||
                    // file phụ đề trùng tên video mà không ghi ngôn ngữ cũng đưa vào cho chọn
                    p.substringAfterLast('/').substringBeforeLast('.') == videoBase
            }
    }

    // ---------------- Phát ----------------

    suspend fun start(t: LoadedTorrent, fileIndex: Int, extraFiles: List<Int>, deletePrevious: Boolean): ActiveTorrent =
        withContext(Dispatchers.IO) {
            ensureStarted()
            val ti = t.info
            val dir = File(baseDir, t.hash).apply { mkdirs() }
            val priorities = Array(ti.numFiles()) { i ->
                if (i == fileIndex || i in extraFiles) Priority.DEFAULT else Priority.IGNORE
            }

            val prev = active
            if (prev != null && prev.hash == t.hash) {
                // Cùng torrent (mở lại, hoặc đổi tập trong gói cả mùa): giữ kết nối, chỉ đổi file.
                if (prev.fileIndex == fileIndex && !prev.closed) return@withContext prev
                prev.closed = true
                prev.handle.prioritizeFiles(priorities)
                return@withContext ActiveTorrent(prev.handle, ti, fileIndex, dir, t.hash).also { active = it }
            }
            stop(deleteFiles = deletePrevious)
            cleanupCache(keepHash = t.hash, keepOthers = !deletePrevious)
            awaitRemoved(ti)

            sm.download(ti, dir, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)

            var handle: TorrentHandle? = null
            for (i in 0 until 100) {
                val h = sm.find(ti.infoHash())
                if (h != null && h.isValid) { handle = h; break }
                delay(100)
            }
            val th = handle ?: throw IOException("libtorrent không thêm được torrent")
            // Torrent đã có sẵn trong phiên (vd đổi tập trong cùng gói): đặt lại ưu tiên file.
            th.prioritizeFiles(priorities)
            th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            ActiveTorrent(th, ti, fileIndex, dir, t.hash).also { active = it }
        }

    fun stop(deleteFiles: Boolean) {
        val a = active ?: return
        active = null
        a.closed = true
        try { sm.remove(a.handle) } catch (_: Exception) {}
        if (deleteFiles) {
            scope.launch {
                delay(2000)
                // Không xoá nếu vừa mở lại chính torrent này (vd chuyển sang tập tiếp theo trong cùng gói).
                if (active?.hash != a.hash) a.dir.deleteRecursively()
            }
        }
    }

    /** Chờ libtorrent gỡ hẳn torrent cũ trước khi thêm lại (gỡ là bất đồng bộ). */
    private suspend fun awaitRemoved(ti: TorrentInfo) {
        for (i in 0 until 40) {
            val h = try { sm.find(ti.infoHash()) } catch (e: Exception) { null }
            if (h == null || !h.isValid) return
            delay(100)
        }
    }

    /** Giữ tối đa 3 torrent gần nhất nếu không xoá sau khi xem. */
    private fun cleanupCache(keepHash: String, keepOthers: Boolean) {
        val dirs = baseDir.listFiles()?.filter { it.isDirectory && it.name != keepHash } ?: return
        val toDelete = if (keepOthers) dirs.sortedByDescending { it.lastModified() }.drop(2) else dirs
        toDelete.forEach { it.deleteRecursively() }
    }

    fun clearAll() {
        stop(deleteFiles = true)
        baseDir.listFiles()?.forEach { it.deleteRecursively() }
        metaDir.listFiles()?.forEach { it.delete() }
    }

    fun cacheBytes(): Long = baseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Chờ đủ dữ liệu đầu file (và vài MB cuối file, nơi MP4/MKV để chỉ mục)
     * trước khi mở trình phát.
     */
    suspend fun prebuffer(a: ActiveTorrent, onProgress: (Float, TorrentStats) -> Unit) {
        val head = minOf(a.fileSize - 1, maxOf(12L * 1024 * 1024, minOf((a.fileSize * 0.01).toLong(), 40L * 1024 * 1024)))
        val tailStart = maxOf(0L, a.fileSize - 3L * 1024 * 1024)
        val headPieces = (a.pieceAt(0)..a.pieceAt(head)).toList()
        val tailPieces = (a.pieceAt(tailStart)..a.pieceAt(a.fileSize - 1)).toList()
        val needed = (headPieces + tailPieces).distinct()
        var d = 0
        headPieces.forEach { p -> a.handle.setPieceDeadline(p, d); d += 120 }
        tailPieces.forEach { p -> a.handle.setPieceDeadline(p, 400) }
        var lastDone = -1
        var lastChange = System.currentTimeMillis()
        while (true) {
            coroutineContext.ensureActive()
            if (a.closed) throw IOException("Đã dừng")
            val done = needed.count { a.handle.havePiece(it) }
            val st = a.stats()
            onProgress(done.toFloat() / needed.size, st)
            if (done == needed.size) break
            if (done != lastDone || st.downloadRate > 20_000) { lastDone = done; lastChange = System.currentTimeMillis() }
            // Đứng im 90 giây (không ai chia sẻ) thì báo lỗi để thử nguồn khác.
            if (System.currentTimeMillis() - lastChange > 90_000) {
                throw IOException("Nguồn này không có người chia sẻ hoạt động (${st.peers} kết nối).")
            }
            delay(400)
        }
    }
}

/** Torrent đang phát: ánh xạ vị trí byte trong file sang piece. */
class ActiveTorrent(
    val handle: TorrentHandle,
    val info: TorrentInfo,
    val fileIndex: Int,
    val dir: File,
    val hash: String,
) {
    private val fs = info.files()
    val fileSize: Long = fs.fileSize(fileIndex)
    private val fileOffset: Long = fs.fileOffset(fileIndex)
    private val pieceLength: Long = info.pieceLength().toLong()
    private val numPieces = info.numPieces()
    val fileName: String = fs.fileName(fileIndex)
    val file: File get() = File(dir, fs.filePath(fileIndex))

    @Volatile
    var closed = false

    @Volatile
    private var lastWindowStart = -1

    fun pieceAt(pos: Long): Int = ((fileOffset + pos.coerceIn(0, fileSize - 1)) / pieceLength).toInt().coerceIn(0, numPieces - 1)

    /** Vị trí (tính trong file) ngay sau piece [p]. */
    fun pieceEndInFile(p: Int): Long = (p + 1) * pieceLength - fileOffset

    /** Đặt hạn chót cho các piece từ vị trí đang đọc trở đi (đọc trước ~[ahead] byte). */
    fun prioritize(pos: Long, ahead: Long = 48L * 1024 * 1024) {
        val first = pieceAt(pos)
        if (first == lastWindowStart) return
        lastWindowStart = first
        val last = pieceAt(minOf(fileSize - 1, pos + ahead))
        var deadline = 0
        try {
            for (p in first..last) {
                if (!handle.havePiece(p)) handle.setPieceDeadline(p, deadline)
                deadline += 200
            }
        } catch (_: Exception) {
        }
    }

    /** Chặn luồng đọc tới khi có piece [p]. Trả false nếu luồng bị huỷ. */
    fun waitForPiece(p: Int, cancelled: () -> Boolean): Boolean {
        val started = System.currentTimeMillis()
        while (true) {
            if (closed || cancelled()) return false
            // Handle có thể vừa bị gỡ khi người xem thoát: coi như luồng đã đóng thay vì ném lỗi native.
            val have = try { handle.isValid && handle.havePiece(p) } catch (_: Exception) { return false }
            if (have) break
            if (System.currentTimeMillis() - started > 10 * 60_000) return false
            Thread.sleep(40)
        }
        return true
    }

    fun stats(): TorrentStats = try {
        val s = handle.status()
        TorrentStats(s.numPeers(), s.numSeeds(), s.downloadPayloadRate().toLong(), s.progress())
    } catch (e: Exception) {
        TorrentStats(0, 0, 0, 0f)
    }

    /** Đọc trọn một file nhỏ trong torrent (phụ đề). */
    suspend fun readSmallFile(index: Int): ByteArray = withContext(Dispatchers.IO) {
        handle.filePriority(index, Priority.TOP_PRIORITY)
        val off = fs.fileOffset(index)
        val size = fs.fileSize(index)
        val first = (off / pieceLength).toInt()
        val last = ((off + maxOf(size - 1, 0)) / pieceLength).toInt()
        for (p in first..last) handle.setPieceDeadline(p, 0)
        val started = System.currentTimeMillis()
        while ((first..last).any { !handle.havePiece(it) }) {
            coroutineContext.ensureActive()
            if (System.currentTimeMillis() - started > 90_000) throw IOException("Tải phụ đề trong torrent quá lâu")
            delay(200)
        }
        File(dir, fs.filePath(index)).readBytes()
    }
}
