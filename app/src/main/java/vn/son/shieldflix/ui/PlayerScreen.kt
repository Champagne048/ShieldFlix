@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package vn.son.shieldflix.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.son.shieldflix.App
import vn.son.shieldflix.MainActivity
import vn.son.shieldflix.data.MediaType
import vn.son.shieldflix.data.PlayRequest
import vn.son.shieldflix.data.Quality
import vn.son.shieldflix.data.SubtitleOption
import vn.son.shieldflix.data.SubtitleService
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.TorrentResult
import vn.son.shieldflix.data.TorrentSource
import vn.son.shieldflix.data.WatchProgress
import vn.son.shieldflix.data.formatSpeed
import vn.son.shieldflix.torrent.ActiveTorrent
import vn.son.shieldflix.torrent.LoadedTorrent
import vn.son.shieldflix.torrent.TorrentStats
import java.io.File
import java.io.IOException
import android.graphics.Color as AColor

private const val PROVIDER_TORRENT = "Torrent"

private sealed class Phase {
    data object Searching : Phase()
    data class Connecting(val msg: String) : Phase()
    data class Buffering(val progress: Float) : Phase()
    data object Playing : Phase()
    data class Failed(val msg: String) : Phase()
}

private fun buildPlayer(ctx: Context): ExoPlayer {
    // Máy chủ stream có thể phải chờ piece vài chục giây khi tua: nới thời gian chờ đọc.
    val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(30_000)
        .setReadTimeoutMs(180_000)
    val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http))
        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(12))
    val renderers = DefaultRenderersFactory(ctx)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(20_000, 120_000, 2_500, 5_000)
        .setBackBuffer(30_000, true)
        .build()
    val player = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setPreferredTextLanguage("vi")
        .setSelectUndeterminedTextLanguage(true)
        .build()
    return player
}

private data class NameGuess(val query: String, val year: String?, val season: Int, val episode: Int)

/** Đoán tên phim/tập từ tên torrent, vd "Dune.Part.Two.2024.1080p.WEB-DL" → "Dune Part Two", 2024. */
private fun guessFromName(name: String): NameGuess {
    val clean = name.replace('.', ' ').replace('_', ' ')
    val se = Regex("[Ss](\\d{1,2})[ ._-]?[Ee](\\d{1,3})").find(clean)
    // Lấy năm cuối cùng hợp lệ (tên phim có thể chứa số dạng năm, vd "Blade Runner 2049 2017").
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").findAll(clean).lastOrNull { it.value.toInt() <= 2030 }
    var cut = clean.length
    se?.let { cut = minOf(cut, it.range.first) }
    year?.let { if (it.range.first > 0) cut = minOf(cut, it.range.first) }
    Regex("\\b(2160p|1080p|720p|480p|bluray|web-?dl|webrip|hdtv|x264|x265|hevc)\\b", RegexOption.IGNORE_CASE)
        .find(clean)?.let { if (it.range.first > 0) cut = minOf(cut, it.range.first) }
    val q = clean.substring(0, cut)
        .replace(Regex("[\\[(].*?[\\])]"), " ")
        .replace(Regex("[\\[\\](){}-]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    return NameGuess(
        query = q.ifBlank { clean.take(40) },
        year = year?.value,
        season = se?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        episode = se?.groupValues?.get(2)?.toIntOrNull() ?: 0,
    )
}

@Composable
fun PlayerScreen(nav: Nav, initial: PlayRequest) {
    val app = App.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val scope = rememberCoroutineScope()
    val version by prefs.version.collectAsState()

    var req by remember { mutableStateOf(initial) }
    var phase by remember { mutableStateOf<Phase>(Phase.Searching) }
    var stats by remember { mutableStateOf(TorrentStats(0, 0, 0, 0f)) }
    var loaded by remember { mutableStateOf<LoadedTorrent?>(null) }
    var active by remember { mutableStateOf<ActiveTorrent?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<TorrentResult>>(emptyList()) }
    var attempt by remember { mutableIntStateOf(0) }
    var sourceLabel by remember { mutableStateOf(initial.result?.title ?: "") }

    var subOptions by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var subFile by remember { mutableStateOf<File?>(null) }
    var subName by remember { mutableStateOf<String?>(null) }
    var subOffset by remember { mutableLongStateOf(0L) }
    var subsEnabled by remember { mutableStateOf(true) }
    var subsLoading by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(true) }
    var reattachJob by remember { mutableStateOf<Job?>(null) }

    val exo = remember { buildPlayer(ctx) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val autoMode = initial.result == null && initial.magnet == null

    fun mediaItem(url: String, sub: File?): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        if (sub != null) {
            b.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                        .setMimeType(SubtitleService.mimeFor(sub))
                        .setLanguage("vi")
                        .setLabel("Tiếng Việt")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        return b.build()
    }

    /** Gắn (lại) phụ đề: dựng lại MediaItem tại đúng vị trí đang xem. */
    fun attach(sub: File?) {
        val url = streamUrl ?: return
        val pos = exo.currentPosition
        val file = sub?.let { app.subtitles.shifted(it, subOffset) }
        exo.setMediaItem(mediaItem(url, file), pos)
        exo.prepare()
    }

    fun setTextEnabled(on: Boolean) {
        subsEnabled = on
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .build()
    }

    suspend fun applySubtitle(opt: SubtitleOption) {
        subsLoading = true
        try {
            val f = if (opt.provider == PROVIDER_TORRENT) {
                val a = active ?: return
                val idx = opt.extra?.toIntOrNull() ?: return
                app.subtitles.saveNormalized(opt.name, a.readSmallFile(idx), "torrent_${a.hash}_$idx")
            } else {
                app.subtitles.download(opt, req.season, req.episode)
            }
            subFile = f
            subName = opt.name
            subOffset = 0
            setTextEnabled(true)
            attach(f)
            toast = "Phụ đề: ${opt.name}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = "Không tải được phụ đề: ${e.message}"
        } finally {
            subsLoading = false
        }
    }

    suspend fun findSubtitles(lt: LoadedTorrent, inTorrent: List<Pair<Int, String>>) {
        subsLoading = true
        val viRe = Regex("vie|viet|\\bvi\\b|vn\\b|tiếng việt", RegexOption.IGNORE_CASE)
        val local = inTorrent.map { (idx, path) ->
            SubtitleOption(
                provider = PROVIDER_TORRENT, id = "t$idx", name = path.substringAfterLast('/'),
                downloads = 0, hearingImpaired = false, extra = idx.toString(),
                match = if (viRe.containsMatchIn(path)) 1.5 else 0.0,
            )
        }
        val online = if (req.tmdbId != 0) {
            try {
                app.subtitles.search(req.type, req.tmdbId, req.imdbId, req.season, req.episode, lt.name)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        val all = (local + online).sortedByDescending { it.match }
        subOptions = all
        subsLoading = false
        // Phụ đề Việt trong torrent ưu tiên nhất, sau đó tới bản online khớp tên phát hành nhất.
        val best = all.firstOrNull { it.provider != PROVIDER_TORRENT || it.match > 0.0 }
        if (best == null) {
            toast = if (!prefs.hasSubtitles && local.isEmpty()) "Chưa có nguồn phụ đề – thêm khoá Subdl/OpenSubtitles trong Cài đặt"
            else "Chưa tìm thấy phụ đề tiếng Việt cho bản này"
            return
        }
        applySubtitle(best)
    }

    suspend fun identify(name: String) {
        try {
            val g = guessFromName(name)
            val wantTv = g.season > 0
            val results = app.tmdb.search(g.query).filter { (it.type == MediaType.TV) == wantTv }
            val found = results.firstOrNull { wantTv || g.year == null || it.year == g.year } ?: results.firstOrNull() ?: return
            req = req.copy(
                tmdbId = found.id, type = found.type, title = found.name, originalTitle = found.originalName,
                year = found.year, backdrop = found.backdrop, poster = found.poster,
                season = if (wantTv) g.season else 0, episode = if (wantTv) g.episode else 0,
            )
        } catch (_: Exception) {
        }
    }

    fun saveProgress() {
        val lt = loaded ?: return
        if (req.tmdbId == 0) return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val sub = if (req.type == MediaType.TV) {
            "T${req.season}:E${req.episode}" + if (req.episodeName.isNotBlank()) " · ${req.episodeName}" else ""
        } else req.year
        prefs.saveProgress(
            WatchProgress(
                key = req.progressKey, tmdbId = req.tmdbId, type = req.type, title = req.title, subtitle = sub,
                backdrop = req.backdrop, poster = req.poster, season = req.season, episode = req.episode,
                positionMs = exo.currentPosition, durationMs = dur, magnet = lt.magnet, torrentName = lt.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---------------- Luồng chính: tìm nguồn → metadata → tải trước → phát ----------------
    LaunchedEffect(attempt) {
        try {
            ended = false
            phase = Phase.Searching
            val source: TorrentSource = when {
                attempt == 0 && req.magnet != null -> TorrentSource.Magnet(req.magnet!!)
                attempt == 0 && req.result != null -> {
                    sourceLabel = req.result!!.title
                    app.torznab.resolve(req.result!!)
                }
                else -> {
                    if (candidates.isEmpty()) {
                        if (req.tmdbId == 0) throw IOException("Magnet này không phản hồi và không có nguồn thay thế.")
                        candidates = if (req.type == MediaType.TV && req.season > 0) {
                            app.torznab.searchEpisode(req.imdbId, req.title, req.originalTitle, req.season, req.episode)
                        } else {
                            app.torznab.searchMovie(req.imdbId, req.title, req.originalTitle, req.year)
                        }
                    }
                    val idx = if (autoMode) attempt else attempt - 1
                    val c = candidates.getOrNull(idx) ?: throw IOException(
                        if (candidates.isEmpty()) "Không tìm thấy nguồn nào còn người chia sẻ." else "Đã thử hết các nguồn tốt nhất.",
                    )
                    sourceLabel = c.title
                    app.torznab.resolve(c)
                }
            }

            phase = Phase.Connecting("Đang kết nối…")
            val lt = app.engine.load(source) { msg -> phase = Phase.Connecting(msg) }
            loaded = lt
            sourceLabel = lt.name
            if (req.tmdbId == 0 && prefs.hasTmdb) identify(lt.name)

            val fileIndex = app.engine.pickFile(lt.info, req.season, req.episode)
            val inTorrentSubs = app.engine.subtitleFiles(lt.info, fileIndex)
            val a = app.engine.start(lt, fileIndex, emptyList(), prefs.deleteAfterWatch)
            active = a

            phase = Phase.Buffering(0f)
            app.engine.prebuffer(a) { p, s ->
                phase = Phase.Buffering(p)
                stats = s
            }

            val url = app.streamServer.urlFor(a)
            streamUrl = url
            exo.setMediaItem(mediaItem(url, null), req.startPositionMs)
            exo.prepare()
            exo.playWhenReady = true
            phase = Phase.Playing
            launch { findSubtitles(lt, inTorrentSubs) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val next = if (autoMode) attempt + 1 else attempt
            if (autoMode && attempt < 3 && candidates.size > next) {
                toast = "Nguồn không phản hồi, đang thử nguồn khác…"
                attempt++
            } else {
                phase = Phase.Failed(e.message ?: "Lỗi không xác định")
            }
        }
    }

    // Lắng nghe trình phát
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) ended = true
            }

            override fun onPlayerError(error: PlaybackException) {
                phase = Phase.Failed(
                    "Không phát được video (${error.errorCodeName}). Shield có thể không giải mã được định dạng " +
                        "hình/tiếng của bản này. Anh thử nguồn khác.",
                )
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Giữ màn hình sáng, dọn dẹp khi thoát
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            saveProgress()
            val dur = exo.duration
            val finished = dur > 0 && exo.currentPosition >= dur * 0.92
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exo.release()
            app.streamServer.source = null
            app.engine.stop(deleteFiles = prefs.deleteAfterWatch && finished)
        }
    }

    // Phím remote: ▲ mở bảng phụ đề, ◀ ▶ tua 10 giây, OK tạm dừng/tiếp tục
    DisposableEffect(activity) {
        activity?.keyInterceptor = interceptor@{ e ->
            if (phase !is Phase.Playing || panelOpen || ended) return@interceptor false
            val pv = playerView ?: return@interceptor false
            val down = e.action == KeyEvent.ACTION_DOWN
            val visible = pv.isControllerFullyVisible
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (!visible) {
                    if (down) panelOpen = true
                    return@interceptor true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> {
                    if (down) panelOpen = true
                    return@interceptor true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (!visible) {
                    if (down) {
                        val d = if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -10_000L else 10_000L
                        exo.seekTo((exo.currentPosition + d).coerceAtLeast(0L))
                        pv.showController()
                    }
                    return@interceptor true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!visible) {
                    if (down) {
                        exo.playWhenReady = !exo.playWhenReady
                        pv.showController()
                    }
                    return@interceptor true
                }
                KeyEvent.KEYCODE_BACK -> if (visible) {
                    if (!down) pv.hideController()
                    return@interceptor true
                }
            }
            false
        }
        onDispose { activity?.keyInterceptor = null }
    }

    // Kiểu chữ phụ đề theo cài đặt
    LaunchedEffect(playerView, version) {
        playerView?.subtitleView?.let { sv ->
            sv.setApplyEmbeddedFontSizes(false)
            sv.setStyle(
                CaptionStyleCompat(
                    AColor.WHITE,
                    if (prefs.subtitleBackground) 0x99000000.toInt() else AColor.TRANSPARENT,
                    AColor.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    AColor.BLACK,
                    Typeface.DEFAULT_BOLD,
                ),
            )
            sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.subtitleSizeSp.toFloat())
            sv.setBottomPaddingFraction(0.07f)
        }
    }

    LaunchedEffect(phase is Phase.Playing) {
        if (phase !is Phase.Playing) return@LaunchedEffect
        launch { delay(7000); showHint = false }
        val a = active
        while (true) {
            delay(1000)
            if (a != null) stats = a.stats()
            if (System.currentTimeMillis() / 1000 % 15 == 0L) saveProgress()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) { delay(3500); toast = null }
    }

    LaunchedEffect(panelOpen) {
        if (!panelOpen) playerView?.requestFocus()
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

    // ---------------- Giao diện ----------------
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val ph = phase) {
            is Phase.Playing -> {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            player = exo
                            useController = true
                            controllerShowTimeoutMs = 4000
                            setShowSubtitleButton(true)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            keepScreenOn = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                        }.also { playerView = it }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                if (isBuffering) BufferingBadge(stats, Modifier.align(Alignment.TopEnd))
                if (showHint && !panelOpen) HintBar(req.displayTitle, Modifier.align(Alignment.TopStart))
                if (ended) {
                    EndedOverlay(
                        req = req,
                        loaded = loaded,
                        onNext = { next -> nav.replace(Screen.Player(next)) },
                        onBack = { nav.pop() },
                    )
                }
            }
            is Phase.Failed -> {
                Backdrop(req)
                ErrorView(
                    ph.msg,
                    onRetry = {
                        phase = Phase.Searching
                        attempt++
                    },
                    onBack = { nav.pop() },
                    retryLabel = "Thử nguồn khác",
                )
            }
            else -> LoadingScreen(req, ph, stats, sourceLabel)
        }

        if (panelOpen) {
            SubtitlePanel(
                options = subOptions,
                current = subName,
                enabled = subsEnabled,
                loading = subsLoading,
                offsetMs = subOffset,
                sizeSp = prefs.subtitleSizeSp,
                onPick = { o -> scope.launch { applySubtitle(o) } },
                onDisable = { setTextEnabled(false) },
                onOffset = { d ->
                    subOffset += d
                    reattachJob?.cancel()
                    reattachJob = scope.launch {
                        delay(800)
                        if (subFile != null) attach(subFile)
                    }
                },
                onSize = { d -> prefs.subtitleSizeSp = prefs.subtitleSizeSp + d },
            )
        }

        toast?.let { t ->
            Text(
                t,
                color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xDD202020)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Backdrop(req: PlayRequest) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Tmdb.img(req.backdrop, "w1280"), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.5f),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black, 0.35f to Color(0xCC000000), 0.8f to Color.Transparent)))
    }
}

@Composable
private fun LoadingScreen(req: PlayRequest, phase: Phase, stats: TorrentStats, label: String) {
    Box(Modifier.fillMaxSize()) {
        Backdrop(req)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp).fillMaxWidth(0.6f)) {
            TitleLogo(req.logo, req.title, modifier = Modifier.fillMaxWidth(0.7f), height = 120.dp, textSize = 44.sp)
            if (req.type == MediaType.TV && req.season > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Mùa ${req.season} · Tập ${req.episode}" + if (req.episodeName.isNotBlank()) "  ·  ${req.episodeName}" else "",
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(28.dp))
            val msg = when (phase) {
                is Phase.Searching -> "Đang tìm nguồn nhiều người chia sẻ nhất…"
                is Phase.Connecting -> phase.msg
                is Phase.Buffering -> "Đang tải trước để phát mượt…"
                else -> ""
            }
            Text(msg, color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            if (phase is Phase.Buffering) {
                LinearProgressIndicator(
                    progress = { phase.progress },
                    color = Palette.Red, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.width(460.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(phase.progress * 100).toInt()}%   ·   ${stats.peers} kết nối   ·   ${stats.seeds} seed   ·   ${formatSpeed(stats.downloadRate)}",
                    color = Palette.Gray, fontSize = 15.sp,
                )
            } else {
                LinearProgressIndicator(
                    color = Palette.Red, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.width(460.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
            }
            if (label.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(label, color = Palette.DimGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tags = Quality.tags(label)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { Badge(it) } }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Bấm Quay lại để huỷ", color = Palette.DimGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BufferingBadge(stats: TorrentStats, modifier: Modifier) {
    Row(
        modifier.padding(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = Palette.Red, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Đang tải · ${stats.peers} kết nối · ${formatSpeed(stats.downloadRate)}", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun HintBar(title: String, modifier: Modifier) {
    Column(
        modifier.padding(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("▲ Phụ đề    ◀ ▶ Tua 10 giây    OK Tạm dừng", color = Palette.Gray, fontSize = 14.sp)
    }
}

/** Hết phim: series thì đếm ngược sang tập sau, phim lẻ thì quay lại. */
@Composable
private fun EndedOverlay(req: PlayRequest, loaded: LoadedTorrent?, onNext: (PlayRequest) -> Unit, onBack: () -> Unit) {
    val app = App.instance
    val next = remember(req, loaded) {
        if (req.type != MediaType.TV || req.season <= 0) null
        else {
            val ep = req.episode + 1
            // Gói cả mùa đã có tập sau thì dùng luôn, khỏi tìm lại.
            val inPack = loaded?.let { lt ->
                val fs = lt.info.files()
                val re = Quality.episodeRegex(req.season, ep)
                app.engine.videoFiles(lt.info).any { re.containsMatchIn(fs.filePath(it)) }
            } == true
            req.copy(
                episode = ep, episodeName = "", result = null, startPositionMs = 0,
                magnet = if (inPack) loaded?.magnet else null,
            )
        }
    }
    var countdown by remember { mutableIntStateOf(10) }
    val fr = remember { FocusRequester() }

    LaunchedEffect(next) {
        if (next == null) { delay(1500); onBack(); return@LaunchedEffect }
        while (countdown > 0) { delay(1000); countdown-- }
        onNext(next)
    }

    if (next != null) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
            Column(Modifier.align(Alignment.BottomEnd).padding(56.dp), horizontalAlignment = Alignment.End) {
                Text("Tập tiếp theo", color = Palette.Gray, fontSize = 16.sp)
                Text("T${next.season}:E${next.episode}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton("Quay lại", onBack)
                    TvButton("▶  Xem tiếp ($countdown)", { onNext(next) }, primary = true, focusRequester = fr)
                }
            }
        }
        AutoFocus(fr)
    }
}

@Composable
private fun SubtitlePanel(
    options: List<SubtitleOption>,
    current: String?,
    enabled: Boolean,
    loading: Boolean,
    offsetMs: Long,
    sizeSp: Int,
    onPick: (SubtitleOption) -> Unit,
    onDisable: () -> Unit,
    onOffset: (Long) -> Unit,
    onSize: (Int) -> Unit,
) {
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(470.dp)
                .background(Color(0xF2141414)).padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text("Phụ đề tiếng Việt", style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    loading -> "Đang tìm/tải phụ đề…"
                    !enabled -> "Đang tắt phụ đề"
                    current != null -> "Đang dùng: $current"
                    else -> "Chưa chọn phụ đề"
                },
                color = Palette.Gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lệch thời gian", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TvChip("−0.5s", false, onClick = { onOffset(-500) }, focusRequester = fr)
                Text("%+.1fs".format(offsetMs / 1000.0), color = Color.White, fontSize = 15.sp, modifier = Modifier.width(56.dp))
                TvChip("+0.5s", false, onClick = { onOffset(500) })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cỡ chữ", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TvChip("A−", false, onClick = { onSize(-2) })
                Text("$sizeSp", color = Color.White, fontSize = 15.sp, modifier = Modifier.width(56.dp))
                TvChip("A+", false, onClick = { onSize(2) })
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { PanelItem("Tắt phụ đề", null, selected = !enabled, onClick = onDisable) }
                items(options.distinctBy { it.provider + it.id }, key = { it.provider + it.id }) { o ->
                    val sub = buildString {
                        append(o.provider)
                        if (o.downloads > 0) append(" · ${o.downloads} lượt tải")
                        if (o.hearingImpaired) append(" · CC")
                        if (o.provider != PROVIDER_TORRENT && o.match > 0.3) append(" · khớp bản phát hành")
                    }
                    PanelItem(o.name, sub, selected = enabled && o.name == current, onClick = { onPick(o) })
                }
                if (options.isEmpty() && !loading) {
                    item { Text("Không có phụ đề tiếng Việt từ các nguồn đã cấu hình.", color = Palette.Gray, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Phụ đề/âm thanh có sẵn trong file: bấm OK để hiện thanh điều khiển, chọn biểu tượng phụ đề hoặc bánh răng.",
                color = Palette.DimGray, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
    }
    AutoFocus(fr)
}

@Composable
private fun PanelItem(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    FocusBox(onClick = onClick, focusedScale = 1.0f, showBorder = false, modifier = Modifier.fillMaxWidth()) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Color.White else Color(0xFF222222)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (f) Color.Black else Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, color = if (f) Color(0xFF444444) else Palette.DimGray, fontSize = 12.sp)
            }
            if (selected) Text("✓", color = if (f) Palette.Red else Palette.Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
