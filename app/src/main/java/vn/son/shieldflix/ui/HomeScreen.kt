package vn.son.shieldflix.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.setValue
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
import vn.son.shieldflix.App
import vn.son.shieldflix.data.MediaType
import vn.son.shieldflix.data.PlayRequest
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.Title
import vn.son.shieldflix.data.TitleRow
import vn.son.shieldflix.data.WatchProgress
import vn.son.shieldflix.web.ConfigServer

/** Giữ dữ liệu trang chủ khi chuyển màn hình để quay lại không phải tải lại. */
private object HomeCache {
    var key: String = ""
    var rows: List<TitleRow>? = null
    var hero: Title? = null
    var focusRow = 0
    var focusCol = 0
}

/** Phần tử trên hero: phim/series hoặc mục "Tiếp tục xem". */
private data class HeroItem(
    val name: String,
    val overview: String,
    val backdrop: String?,
    val meta: String,
)

private fun Title.hero() = HeroItem(
    name, overview, backdrop,
    listOfNotNull(
        year.ifBlank { null },
        if (type == MediaType.TV) "Series" else "Phim",
        if (rating > 0) "★ %.1f".format(rating) else null,
    ).joinToString("  ·  "),
)

@Composable
fun HomeScreen(nav: Nav) {
    val app = App.instance
    val version by app.prefs.version.collectAsState()
    val tmdbKey = remember(version) { app.prefs.tmdbKey }
    val progress = remember(version) { app.prefs.progressList() }

    var rows by remember { mutableStateOf(HomeCache.rows.takeIf { HomeCache.key == tmdbKey }) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(HomeCache.hero?.hero()) }
    var hero by remember { mutableStateOf(focused) }

    LaunchedEffect(tmdbKey, reload) {
        if (tmdbKey.isBlank()) return@LaunchedEffect
        if (rows != null && HomeCache.key == tmdbKey && reload == 0) return@LaunchedEffect
        error = null
        try {
            val r = app.tmdb.homeRows()
            rows = r
            HomeCache.rows = r
            HomeCache.key = tmdbKey
            if (hero == null) hero = r.firstOrNull()?.items?.firstOrNull()?.hero()
        } catch (e: Exception) {
            error = e.message ?: "Không tải được danh sách phim"
        }
    }

    // Hero đổi theo ô đang chọn, chờ một nhịp để lướt nhanh không bị giật.
    LaunchedEffect(focused) {
        delay(280)
        if (focused != null) hero = focused
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        // Ảnh nền hero
        Crossfade(targetState = hero?.backdrop, animationSpec = tween(450), label = "hero") { bd ->
            Box(Modifier.fillMaxSize()) {
                if (bd != null) {
                    AsyncImage(
                        model = Tmdb.img(bd, "w1280"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.72f),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Bg, 0.35f to Palette.Bg.copy(alpha = 0.85f), 0.65f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 0.62f to Palette.Bg),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            TopBar(nav, current = "home")

            when {
                tmdbKey.isBlank() -> Onboarding(nav)
                error != null && rows == null -> ErrorView(error!!, onRetry = { reload++ })
                rows == null -> LoadingView("Đang tải danh sách phim…")
                else -> {
                    HeroInfo(hero)
                    RowsList(
                        rows = rows!!,
                        progress = progress,
                        onFocusTitle = { t -> focused = t.hero(); HomeCache.hero = t },
                        onFocusProgress = { p -> focused = HeroItem(p.title, p.subtitle, p.backdrop, "Tiếp tục xem · ${vn.son.shieldflix.data.formatTime(p.positionMs)}") },
                        onOpen = { t -> nav.push(Screen.Detail(t.type, t.id)) },
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
fun TopBar(nav: Nav, current: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SHIELDFLIX", color = Palette.Red, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(36.dp))
        NavTab("Trang chủ", current == "home") { if (current != "home") nav.goHome() }
        NavTab("Tìm kiếm", current == "search") { if (current != "search") nav.push(Screen.Search) }
        NavTab("Cài đặt", current == "settings") { if (current != "settings") nav.push(Screen.Settings) }
    }
}

@Composable
private fun NavTab(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(onClick = onClick, shape = RoundedCornerShape(4.dp), focusedScale = 1.05f, showBorder = false) { f ->
        Text(
            text,
            modifier = Modifier.background(if (f) Color.White else Color.Transparent).padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (f) Color.Black else if (selected) Color.White else Palette.Gray,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun HeroInfo(hero: HeroItem?) {
    Column(Modifier.padding(start = 48.dp, top = 28.dp).fillMaxWidth(0.48f).height(196.dp)) {
        if (hero != null) {
            Text(
                hero.name, style = MaterialTheme.typography.displaySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(hero.meta, color = Palette.Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                hero.overview, color = Color(0xFFDDDDDD), fontSize = 15.sp,
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
    val restore = remember { FocusRequester() }
    val offset = if (progress.isNotEmpty()) 1 else 0
    // Hàng/cột cần trả focus về khi quay lại trang chủ.
    val targetRow = HomeCache.focusRow.coerceAtMost(rows.size - 1 + offset)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (progress.isNotEmpty()) {
            item(key = "continue") {
                RowBlock("Tiếp tục xem") {
                    val rs = rememberLazyListState()
                    LazyRow(
                        state = rs,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(progress, key = { _, p -> p.key }) { col, p ->
                            ContinueCard(
                                p,
                                onClick = { onResume(p) },
                                onFocus = { HomeCache.focusRow = 0; HomeCache.focusCol = col; onFocusProgress(p) },
                                focusRequester = if (targetRow == 0 && col == HomeCache.focusCol.coerceAtMost(progress.size - 1)) restore else null,
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(rows, key = { _, r -> r.title }) { idx, row ->
            val rowIndex = idx + offset
            RowBlock(row.title) {
                val rs = rememberLazyListState()
                LazyRow(
                    state = rs,
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(row.items, key = { _, t -> "${t.type}${t.id}" }) { col, t ->
                        PosterCard(
                            t,
                            onClick = { onOpen(t) },
                            onFocus = { HomeCache.focusRow = rowIndex; HomeCache.focusCol = col; onFocusTitle(t) },
                            focusRequester = if (rowIndex == targetRow && col == HomeCache.focusCol.coerceAtMost(row.items.size - 1)) restore else null,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Cuộn tới hàng đã chọn trước đó rồi đặt focus lại.
        if (targetRow > 0) listState.scrollToItem(targetRow)
        // requestFocus() không báo lỗi khi item chưa gắn vào cây giao diện, nên thử vài lần trong ~0,5 giây.
        for (wait in longArrayOf(60, 150, 300)) {
            delay(wait)
            try { restore.requestFocus() } catch (_: Exception) {}
        }
    }
}

@Composable
private fun RowBlock(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, modifier = Modifier.padding(start = 52.dp), style = MaterialTheme.typography.headlineSmall, fontSize = 19.sp)
        content()
    }
}

@Composable
private fun Onboarding(nav: Nav) {
    val app = App.instance
    val ip = remember { ConfigServer.lanAddress() ?: "<IP Shield>" }
    val fr = remember { FocusRequester() }
    Column(Modifier.padding(start = 48.dp, top = 48.dp).fillMaxWidth(0.62f)) {
        Text("Chào anh! Cần cài đặt một chút", style = MaterialTheme.typography.displaySmall, fontSize = 34.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Mở trình duyệt trên điện thoại hoặc máy tính cùng mạng Wi-Fi, vào địa chỉ:",
            color = Color(0xFFDDDDDD), fontSize = 18.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "http://$ip:${ConfigServer.PORT}",
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Mã PIN: ${app.prefs.pin}", color = Palette.Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Nhập khoá TMDB (bắt buộc, miễn phí), nguồn Torznab (Jackett/Prowlarr) và khoá phụ đề. " +
                "Lưu xong, màn hình này tự tải danh sách phim.",
            color = Palette.Gray, fontSize = 16.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        TvButton("Mở Cài đặt trên TV", { nav.push(Screen.Settings) }, primary = true, focusRequester = fr)
    }
    AutoFocus(fr)
}
