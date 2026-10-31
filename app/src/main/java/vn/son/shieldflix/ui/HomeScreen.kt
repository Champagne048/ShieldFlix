package vn.son.shieldflix.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.son.shieldflix.App
import vn.son.shieldflix.data.MediaType
import vn.son.shieldflix.data.PlayRequest
import vn.son.shieldflix.data.RowStyle
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.Title
import vn.son.shieldflix.data.TitleRow
import vn.son.shieldflix.data.WatchProgress

/** Giữ dữ liệu trang chủ khi chuyển màn hình để quay lại không phải tải lại. */
private object HomeCache {
    var key: String = ""
    var rows: List<TitleRow>? = null
    var hero: HeroItem? = null
    var focusRow = 0
    var focusCol = 0
    val rowCols = HashMap<Int, Int>()
}

/** Phần tử trên hero: phim/series hoặc mục "Tiếp tục xem". */
private data class HeroItem(
    val name: String,
    val overview: String,
    val backdrop: String?,
    val meta: String,
    val logo: String?,
    val title: Title? = null,
)

private fun Title.hero() = HeroItem(
    name, overview, backdrop,
    listOfNotNull(
        if (rating > 0) "★ %.1f".format(rating) else null,
        year.ifBlank { null },
        if (type == MediaType.TV) "Series" else "Phim",
        genres.take(3).joinToString(" · ").ifBlank { null },
    ).joinToString("   "),
    logo,
    this,
)

@Composable
fun HomeScreen(nav: Nav) {
    val app = App.instance
    val version by app.prefs.version.collectAsState()
    val sourceKey = remember(version) { app.catalog.sourceKey }
    val progress = remember(version) { app.prefs.progressList() }
    val hasSources = remember(version) { app.prefs.hasSources }

    var rows by remember { mutableStateOf(HomeCache.rows.takeIf { HomeCache.key == sourceKey }) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(HomeCache.hero) }
    var hero by remember { mutableStateOf(focused) }

    LaunchedEffect(sourceKey, reload) {
        if (rows != null && HomeCache.key == sourceKey && reload == 0) return@LaunchedEffect
        error = null
        try {
            val r = app.catalog.homeRows()
            rows = r
            HomeCache.rows = r
            HomeCache.key = sourceKey
            HomeCache.rowCols.clear()
            if (hero == null) hero = r.firstOrNull()?.items?.firstOrNull()?.hero()
        } catch (e: Exception) {
            error = "Không tải được danh sách phim. Kiểm tra Shield đã có Internet chưa.\n(${e.message})"
        }
    }

    // Hero đổi theo ô đang chọn, chờ một nhịp để lướt nhanh không bị giật.
    LaunchedEffect(focused) {
        delay(300)
        val f = focused ?: return@LaunchedEffect
        hero = f
        // Phim từ TMDB chưa có logo: lấy thêm để hero giống Netflix.
        if (f.logo == null && f.title != null) {
            val logo = app.catalog.logoFor(f.title)
            if (logo != null && hero == f) hero = f.copy(logo = logo)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        // Ảnh nền hero phủ gần hết màn hình, mờ dần sang trái và xuống dưới như Netflix.
        Crossfade(targetState = hero?.backdrop, animationSpec = tween(500), label = "hero") { bd ->
            Box(Modifier.fillMaxSize()) {
                if (bd != null) {
                    AsyncImage(
                        model = Tmdb.img(bd, "w1280"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.80f).fillMaxHeight(0.80f),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Bg, 0.22f to Palette.Bg.copy(alpha = 0.92f), 0.42f to Palette.Bg.copy(alpha = 0.45f), 0.62f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x99000000), 0.14f to Color.Transparent, 0.45f to Color.Transparent, 0.74f to Palette.Bg),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            TopBar(nav, current = "home", showSetup = !hasSources)

            when {
                error != null && rows == null -> ErrorView(error!!, onRetry = { reload++ })
                rows == null -> LoadingView("Đang tải danh sách phim…")
                else -> {
                    HeroInfo(hero)
                    RowsList(
                        rows = rows!!,
                        progress = progress,
                        onFocusTitle = { t -> val h = t.hero(); focused = h; HomeCache.hero = h },
                        onFocusProgress = { p ->
                            val h = HeroItem(p.title, p.subtitle, p.backdrop, "Tiếp tục xem · ${vn.son.shieldflix.data.formatTime(p.positionMs)}", null)
                            focused = h; HomeCache.hero = h
                        },
                        onOpen = { t -> nav.push(Screen.Detail(t.type, t.id, t.imdbId)) },
                        onResume = { p -> nav.push(Screen.Player(resumeRequest(p))) },
                    )
                }
            }
        }
    }
}

fun resumeRequest(p: WatchProgress) = PlayRequest(
    tmdbId = p.tmdbId, type = p.type, title = p.title, originalTitle = p.title, year = "",
    imdbId = null, season = p.season, episode = p.episode,
    backdrop = p.backdrop, poster = p.poster,
    magnet = p.magnet.ifBlank { null }, startPositionMs = p.positionMs,
)

@Composable
fun TopBar(nav: Nav, current: String, showSetup: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 56.dp, end = 56.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SHIELDFLIX", color = Palette.Red, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        Spacer(Modifier.width(40.dp))
        NavTab("Trang chủ", current == "home") { if (current != "home") nav.goHome() }
        NavTab("Tìm kiếm", current == "search") { if (current != "search") nav.push(Screen.Search) }
        NavTab("Cài đặt", current == "settings") { if (current != "settings") nav.push(Screen.Settings) }
        Spacer(Modifier.weight(1f))
        if (showSetup) {
            FocusBox(onClick = { nav.push(Screen.Settings) }, shape = RoundedCornerShape(4.dp), focusedScale = 1.05f, showBorder = false) { f ->
                Text(
                    "Thiết lập nguồn phim",
                    modifier = Modifier.background(if (f) Color.White else Palette.Red).padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (f) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NavTab(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(onClick = onClick, shape = RoundedCornerShape(4.dp), focusedScale = 1.05f, showBorder = false) { f ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                modifier = Modifier.background(if (f) Color.White else Color.Transparent).padding(horizontal = 14.dp, vertical = 6.dp),
                color = if (f) Color.Black else if (selected) Color.White else Color(0xFFCCCCCC),
                fontSize = 17.sp,
                fontWeight = if (selected || f) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HeroInfo(hero: HeroItem?) {
    Column(
        Modifier.padding(start = 56.dp, top = 10.dp).fillMaxWidth(0.42f).height(206.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (hero != null) {
            TitleLogo(hero.logo, hero.name, modifier = Modifier.fillMaxWidth(0.9f), height = 104.dp, textSize = 42.sp)
            Spacer(Modifier.height(14.dp))
            Text(hero.meta, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                hero.overview, color = Color(0xFFE0E0E0), fontSize = 15.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun RowsList(
    rows: List<TitleRow>,
    progress: List<WatchProgress>,
    onFocusTitle: (Title) -> Unit,
    onFocusProgress: (WatchProgress) -> Unit,
    onOpen: (Title) -> Unit,
    onResume: (WatchProgress) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val offset = if (progress.isNotEmpty()) 1 else 0
    val rowCount = rows.size + offset
    // Mỗi hàng nhớ cột đang chọn (giống Netflix); focus vào hàng là về đúng cột đó.
    val entries = remember(rowCount) { List(rowCount) { FocusRequester() } }
    val targetRow = HomeCache.focusRow.coerceIn(0, maxOf(0, rowCount - 1))
    var activeRow by remember { mutableIntStateOf(targetRow) }
    var gotFocus by remember { mutableStateOf(false) }

    fun colOf(r: Int) = HomeCache.rowCols[r] ?: 0

    fun onItemFocus(r: Int, col: Int) {
        gotFocus = true
        HomeCache.focusRow = r
        HomeCache.focusCol = col
        HomeCache.rowCols[r] = col
        if (r != activeRow) {
            activeRow = r
            // Hàng đang chọn luôn nằm ngay dưới phần giới thiệu phim.
            scope.launch { listState.animateScrollToItem(r) }
        }
    }

    // Lên/xuống giữa các hàng tự xử lý: hàng kế tiếp có thể chưa hiện trên màn hình.
    fun moveTo(r: Int) {
        if (r !in 0 until rowCount) return
        scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == r }) listState.scrollToItem(r)
            withFrameNanos { }
            try { entries[r].requestFocus() } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = activeRow > 0) { moveTo(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 400.dp),
    ) {
        if (progress.isNotEmpty()) {
            item(key = "continue") {
                RowBlock("Tiếp tục xem", 0, rowCount, ::moveTo) {
                    LazyRow(
                        state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, colOf(0) - 1)),
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(progress, key = { _, p -> p.key }) { col, p ->
                            ContinueCard(
                                p,
                                onClick = { onResume(p) },
                                onFocus = { onItemFocus(0, col); onFocusProgress(p) },
                                focusRequester = if (col == colOf(0).coerceAtMost(progress.size - 1)) entries[0] else null,
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(rows, key = { _, r -> r.title }) { idx, row ->
            val r = idx + offset
            RowBlock(row.title, r, rowCount, ::moveTo) {
                LazyRow(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, colOf(r) - 1)),
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (row.style == RowStyle.TOP10) 4.dp else 10.dp),
                ) {
                    itemsIndexed(row.items, key = { _, t -> t.key }) { col, t ->
                        val fr = if (col == colOf(r).coerceAtMost(row.items.size - 1)) entries[r] else null
                        val onFocus = { onItemFocus(r, col); onFocusTitle(t) }
                        if (row.style == RowStyle.TOP10) {
                            Top10Card(col + 1, t, onClick = { onOpen(t) }, onFocus = onFocus, focusRequester = fr)
                        } else {
                            LandscapeCard(t, onClick = { onOpen(t) }, onFocus = onFocus, focusRequester = fr)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Đặt focus về hàng/cột cũ. Lúc app vừa mở, cửa sổ có thể chưa sẵn sàng nên thử lại tới khi được (tối đa ~4 giây).
        if (targetRow > 0) listState.scrollToItem(targetRow)
        repeat(20) {
            if (gotFocus) return@LaunchedEffect
            delay(if (it == 0) 60 else 200)
            try { entries[targetRow].requestFocus() } catch (_: Exception) {}
        }
    }
}

@Composable
private fun RowBlock(title: String, row: Int, rowCount: Int, moveTo: (Int) -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionDown -> { if (row + 1 < rowCount) moveTo(row + 1); true }
                // Hàng đầu: để remote đi lên thanh menu như bình thường.
                Key.DirectionUp -> if (row == 0) false else { moveTo(row - 1); true }
                else -> false
            }
        },
    ) {
        Text(title, modifier = Modifier.padding(start = 58.dp, top = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5E5E5))
        content()
    }
}
