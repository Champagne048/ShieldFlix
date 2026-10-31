package vn.son.shieldflix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import vn.son.shieldflix.App
import vn.son.shieldflix.data.Episode
import vn.son.shieldflix.data.MediaType
import vn.son.shieldflix.data.PlayRequest
import vn.son.shieldflix.data.Quality
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.TitleDetail
import vn.son.shieldflix.data.TorrentResult
import vn.son.shieldflix.data.WatchProgress
import vn.son.shieldflix.data.formatSize
import vn.son.shieldflix.data.formatTime

private val detailCache = HashMap<String, TitleDetail>()
private val seasonCache = HashMap<String, List<Episode>>()

@Composable
fun DetailScreen(nav: Nav, type: MediaType, id: Int, imdbId: String? = null) {
    val app = App.instance
    val cacheKey = "$type:$id:${imdbId ?: ""}:${app.catalog.sourceKey}"
    var detail by remember { mutableStateOf(detailCache[cacheKey]) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val version by app.prefs.version.collectAsState()
    val progressAll = remember(version) { app.prefs.progressList().filter { it.tmdbId == id && it.type == type } }

    LaunchedEffect(cacheKey, reload) {
        if (detail != null) return@LaunchedEffect
        try {
            detail = app.catalog.detail(type, id, imdbId).also { detailCache[cacheKey] = it }
        } catch (e: Exception) {
            error = e.message
        }
    }

    val d = detail
    when {
        d == null && error != null -> ErrorView(error!!, onRetry = { error = null; reload++ }, onBack = { nav.pop() })
        d == null -> LoadingView()
        else -> DetailContent(nav, d, progressAll)
    }
}

@Composable
private fun DetailContent(nav: Nav, d: TitleDetail, progressAll: List<WatchProgress>) {
    val app = App.instance
    val t = d.title
    val isTv = t.type == MediaType.TV
    val latest = progressAll.firstOrNull()

    var season by rememberSaveable { mutableIntStateOf(latest?.season?.takeIf { it > 0 } ?: d.seasons.firstOrNull()?.number ?: 1) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var selectedEp by rememberSaveable { mutableIntStateOf(latest?.episode?.takeIf { it > 0 } ?: 1) }
    var showSources by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }

    LaunchedEffect(t.key, season) {
        if (!isTv) return@LaunchedEffect
        val key = "${t.key}:$season"
        episodes = seasonCache[key]
        if (episodes == null) {
            episodes = try { app.catalog.episodes(d, season).also { seasonCache[key] = it } } catch (e: Exception) { emptyList() }
        }
    }

    fun request(ep: Int = selectedEp, s: Int = season, result: TorrentResult? = null, fromStart: Boolean = false): PlayRequest {
        val prog = if (fromStart) null else progressAll.firstOrNull { it.key == WatchProgress.keyOf(t.type, t.id, if (isTv) s else 0, if (isTv) ep else 0) }
        val epName = episodes?.firstOrNull { it.number == ep }?.name ?: ""
        return PlayRequest(
            tmdbId = t.id, type = t.type, title = t.name, originalTitle = t.originalName, year = t.year,
            imdbId = d.imdbId, season = if (isTv) s else 0, episode = if (isTv) ep else 0, episodeName = epName,
            backdrop = t.backdrop, poster = t.poster, logo = t.logo, result = result,
            // Tiếp tục xem: dùng lại đúng torrent đã xem dở (chỉ khi không chọn nguồn khác).
            magnet = if (result == null) prog?.magnet?.ifBlank { null } else null,
            startPositionMs = if (result == null) prog?.positionMs ?: 0 else 0,
        )
    }

    val resumeHere = progressAll.firstOrNull { it.key == WatchProgress.keyOf(t.type, t.id, if (isTv) season else 0, if (isTv) selectedEp else 0) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        AsyncImage(
            model = Tmdb.img(t.backdrop, "w1280"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.78f).fillMaxHeight(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Bg, 0.25f to Palette.Bg.copy(alpha = 0.95f), 0.5f to Palette.Bg.copy(alpha = 0.55f), 0.72f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x88000000), 0.15f to Color.Transparent, 0.55f to Color.Transparent, 1f to Palette.Bg)))

        // Khi mở danh sách nguồn thì ẩn nội dung phía sau để remote không lạc focus ra ngoài.
        if (!showSources) Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 56.dp, top = 40.dp, bottom = 40.dp),
        ) {
            TitleLogo(t.logo, t.name, modifier = Modifier.fillMaxWidth(0.36f), height = 100.dp, textSize = 42.sp)
            Spacer(Modifier.height(6.dp))
            if (t.originalName != t.name || t.logo != null) {
                Text(if (t.originalName != t.name) "${t.name} · ${t.originalName}" else t.name, color = Palette.Gray, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (t.rating > 0) Text("★ %.1f".format(t.rating), color = Palette.Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (t.year.isNotBlank()) Text(t.year, color = Color.White, fontSize = 16.sp)
                d.certification?.let { Badge(it, Color.Transparent) }
                if (isTv) Text("${d.seasons.size} mùa", color = Color.White, fontSize = 16.sp)
                d.runtimeMin?.let { Text(if (it >= 60) "${it / 60} giờ ${it % 60} phút" else "$it phút", color = Color.White, fontSize = 16.sp) }
                Badge("HD")
            }
            Spacer(Modifier.height(6.dp))
            if (d.genres.isNotEmpty()) Text(d.genres.joinToString(" · "), color = Palette.Gray, fontSize = 15.sp)
            d.tagline?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFCCCCCC), fontStyle = FontStyle.Italic, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t.overview, color = Color(0xFFE5E5E5), fontSize = 16.sp, lineHeight = 23.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.55f),
            )
            if (d.cast.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Diễn viên: " + d.cast.joinToString(", "), color = Palette.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(0.55f))
            }
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val label = when {
                    resumeHere != null -> "▶  Tiếp tục ${formatTime(resumeHere.positionMs)}"
                    isTv -> "▶  Phát T$season:E$selectedEp"
                    else -> "▶  Phát"
                }
                TvButton(label, { nav.push(Screen.Player(request())) }, primary = true, focusRequester = playFr)
                if (resumeHere != null) TvButton("Phát từ đầu", { nav.push(Screen.Player(request(fromStart = true))) })
                TvButton("Chọn nguồn", { showSources = true })
            }

            if (isTv && d.seasons.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 48.dp, top = 4.dp, bottom = 4.dp)) {
                    items(d.seasons, key = { it.number }) { s ->
                        TvChip("Mùa ${s.number}", selected = s.number == season, onClick = { season = s.number; selectedEp = 1 })
                    }
                }
                Spacer(Modifier.height(12.dp))
                val eps = episodes
                if (eps == null) {
                    Text("Đang tải danh sách tập…", color = Palette.Gray)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(end = 48.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        itemsIndexed(eps, key = { _, e -> e.number }) { _, e ->
                            val prog = progressAll.firstOrNull { it.season == season && it.episode == e.number }
                            EpisodeCard(
                                e, prog,
                                selected = e.number == selectedEp,
                                onFocus = { selectedEp = e.number },
                                onClick = { nav.push(Screen.Player(request(ep = e.number))) },
                            )
                        }
                    }
                }
            }
        }

        if (showSources) {
            SourcesOverlay(
                req = request(),
                onPick = { r -> showSources = false; nav.push(Screen.Player(request(result = r))) },
                onClose = { showSources = false },
            )
        }
    }
    if (!showSources) AutoFocus(playFr, key = showSources)
}

@Composable
private fun EpisodeCard(e: Episode, prog: WatchProgress?, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(250.dp)) {
        FocusBox(onClick = onClick, onFocus = onFocus, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize().background(Palette.Card)) {
                AsyncImage(
                    model = Tmdb.img(e.still, "w500"), contentDescription = e.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                if (prog != null && prog.durationMs > 0) {
                    ProgressBar(prog.positionMs.toFloat() / prog.durationMs, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${e.number}. ${e.name}", color = if (selected) Color.White else Palette.Gray,
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(e.overview, color = Palette.DimGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
    }
}

/** Danh sách nguồn torrent, xếp theo seed và chất lượng. */
@Composable
fun SourcesOverlay(req: PlayRequest, onPick: (TorrentResult) -> Unit, onClose: () -> Unit) {
    val app = App.instance
    var results by remember { mutableStateOf<List<TorrentResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFr = remember { FocusRequester() }

    BackHandler { onClose() }

    LaunchedEffect(req.tmdbId, req.season, req.episode) {
        try {
            results = if (req.type == MediaType.TV && req.season > 0) {
                app.torznab.searchEpisode(req.imdbId, req.title, req.originalTitle, req.season, req.episode)
            } else {
                app.torznab.searchMovie(req.imdbId, req.title, req.originalTitle, req.year)
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xF0101010))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Text("Chọn nguồn · ${req.displayTitle}", style = MaterialTheme.typography.headlineSmall, fontSize = 26.sp)
            Text("Xếp theo số người chia sẻ (seed) và chất lượng ưu tiên", color = Palette.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            val list = results
            when {
                error != null -> ErrorView(error!!, onBack = onClose)
                list == null -> LoadingView("Đang tìm nguồn…")
                list.isEmpty() -> ErrorView("Không có nguồn nào còn người chia sẻ.", onBack = onClose)
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(list) { i, r ->
                            SourceRow(r, onClick = { onPick(r) }, focusRequester = if (i == 0) firstFr else null)
                        }
                    }
                    AutoFocus(firstFr, key = list.size)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(r: TorrentResult, onClick: () -> Unit, focusRequester: FocusRequester?) {
    FocusBox(
        onClick = onClick, focusRequester = focusRequester, focusedScale = 1.01f,
        modifier = Modifier.fillMaxWidth(),
    ) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Palette.CardFocused else Palette.Card).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(
                if (r.quality == "?") "SD?" else r.quality,
                color = if (r.quality == "2160p") Color(0xFFB8860B) else if (r.quality == "1080p") Palette.Red else Color(0xFF555555),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Quality.tags(r.title).forEach { Badge(it) }
                    Text(r.indexer, color = Palette.DimGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("▲ ${r.seeders}", color = Palette.Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(formatSize(r.sizeBytes), color = Palette.Gray, fontSize = 13.sp)
            }
        }
    }
}
