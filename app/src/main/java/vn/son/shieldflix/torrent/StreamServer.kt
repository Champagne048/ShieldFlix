package vn.son.shieldflix.torrent

import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLEncoder

/**
 * Máy chủ HTTP chỉ nghe trên 127.0.0.1: ExoPlayer đọc http://127.0.0.1:<port>/stream/<tên file>
 * như một video bình thường; mỗi lần tua là một yêu cầu Range mới, ta ưu tiên tải piece ở đó.
 */
class StreamServer : NanoHTTPD("127.0.0.1", 0) {

    @Volatile
    var source: ActiveTorrent? = null

    fun startServer() = start(SOCKET_READ_TIMEOUT, true)

    fun urlFor(a: ActiveTorrent): String {
        source = a
        return "http://127.0.0.1:$listeningPort/stream/${a.hash}/${a.fileIndex}/" +
            URLEncoder.encode(a.fileName, "UTF-8").replace("+", "%20")
    }

    override fun serve(session: IHTTPSession): Response {
        val a = source
        if (a == null || a.closed || !session.uri.startsWith("/stream/${a.hash}/${a.fileIndex}/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không có video")
        }
        val total = a.fileSize
        val mime = mimeOf(a.fileName)
        val range = session.headers["range"]

        var start = 0L
        var end = total - 1
        var partial = false
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(',').trim()
            val s = spec.substringBefore('-')
            val e = spec.substringAfter('-', "")
            if (s.isEmpty() && e.isNotEmpty()) {
                // bytes=-500: 500 byte cuối
                start = maxOf(0L, total - e.toLong())
            } else {
                start = s.toLongOrNull() ?: 0L
                if (e.isNotEmpty()) end = minOf(e.toLongOrNull() ?: end, total - 1)
            }
            partial = true
        }
        if (start >= total || start > end) {
            val r = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
            r.addHeader("Content-Range", "bytes */$total")
            return r
        }

        val length = end - start + 1
        a.prioritize(start)
        val status = if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val resp = newFixedLengthResponse(status, mime, TorrentInputStream(a, start, end), length)
        resp.addHeader("Accept-Ranges", "bytes")
        if (partial) resp.addHeader("Content-Range", "bytes $start-$end/$total")
        resp.addHeader("Cache-Control", "no-cache")
        return resp
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        "wmv" -> "video/x-ms-wmv"
        else -> "application/octet-stream"
    }
}

/** Luồng đọc file đang tải: chờ piece có đủ rồi mới trả dữ liệu. */
class TorrentInputStream(
    private val a: ActiveTorrent,
    start: Long,
    private val endInclusive: Long,
) : InputStream() {

    private var pos = start
    private var raf: RandomAccessFile? = null

    @Volatile
    private var closedStream = false

    override fun read(): Int {
        val b = ByteArray(1)
        val n = read(b, 0, 1)
        return if (n <= 0) -1 else (b[0].toInt() and 0xff)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos > endInclusive) return -1
        val piece = a.pieceAt(pos)
        a.prioritize(pos)
        if (!a.waitForPiece(piece) { closedStream }) throw IOException("Luồng đã đóng")
        val limit = minOf(a.pieceEndInFile(piece), endInclusive + 1)
        val toRead = minOf(len.toLong(), limit - pos).toInt()
        if (toRead <= 0) return -1
        val f = raf ?: RandomAccessFile(a.file, "r").also { raf = it }
        f.seek(pos)
        val n = f.read(b, off, toRead)
        if (n < 0) return -1
        pos += n
        return n
    }

    override fun available(): Int = 0

    override fun close() {
        closedStream = true
        try { raf?.close() } catch (_: Exception) {}
        raf = null
    }
}
