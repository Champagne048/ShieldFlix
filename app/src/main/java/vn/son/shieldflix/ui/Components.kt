package vn.son.shieldflix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import vn.son.shieldflix.data.Tmdb
import vn.son.shieldflix.data.Title
import vn.son.shieldflix.data.WatchProgress

/**
 * Ô bấm được bằng remote: phóng to và viền trắng khi được chọn (giống Netflix trên TV).
 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    shape: Shape = RoundedCornerShape(6.dp),
    focusedScale: Float = 1.08f,
    focusRequester: FocusRequester? = null,
    showBorder: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "scale")
    val fr = focusRequester ?: remember { FocusRequester() }
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (focused && showBorder) 3.dp else 0.dp,
                color = if (focused && showBorder) Color.White else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .focusRequester(fr)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
    ) {
        content(focused)
    }
}

@Composable
fun PosterCard(
    t: Title,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    width: Dp = 136.dp,
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick,
        onFocus = onFocus,
        focusRequester = focusRequester,
        modifier = Modifier.width(width).aspectRatio(2f / 3f),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Card)) {
            Text(
                t.name, Modifier.align(Alignment.Center).padding(8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            AsyncImage(
                model = Tmdb.img(t.poster, "w342"),
                contentDescription = t.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Logo tên phim (ảnh trong suốt như Netflix); không có hoặc lỗi thì hiện chữ to. */
@Composable
fun TitleLogo(
    logo: String?,
    name: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    textSize: TextUnit = 46.sp,
) {
    var failed by remember(logo) { mutableStateOf(logo.isNullOrBlank()) }
    if (failed) {
        Text(
            name, modifier = modifier,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            fontSize = textSize, lineHeight = textSize * 1.1f, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    } else {
        AsyncImage(
            model = logo,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onState = { if (it is AsyncImagePainter.State.Error) failed = true },
            modifier = modifier.height(height),
        )
    }
}

/** Thẻ ngang 16:9 kiểu Netflix: ảnh nền phim + logo tên phim. */
@Composable
fun LandscapeCard(
    t: Title,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    width: Dp = 268.dp,
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick, onFocus = onFocus, focusRequester = focusRequester,
        shape = RoundedCornerShape(5.dp), focusedScale = 1.1f,
        modifier = Modifier.width(width).aspectRatio(16f / 9f),
    ) { focused ->
        Box(Modifier.fillMaxSize().background(Palette.Card)) {
            AsyncImage(
                model = Tmdb.img(t.backdrop ?: t.poster, "w780"),
                contentDescription = t.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
            Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(0.62f)) {
                TitleLogo(t.logo, t.name, height = 46.dp, textSize = 16.sp)
            }
            if (focused && t.rating > 0) {
                Text(
                    "★ %.1f".format(t.rating),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Ô Top 10: số thứ tự lớn viền xám + poster dọc, giống hàng Top 10 của Netflix. */
@Composable
fun Top10Card(
    rank: Int,
    t: Title,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val numberStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 168.sp, letterSpacing = (-14).sp)
    Box(Modifier.width(if (rank >= 10) 262.dp else 214.dp).height(194.dp)) {
        Box(Modifier.align(Alignment.BottomStart).offset(y = 34.dp)) {
            Text("$rank", style = numberStyle.copy(color = Palette.Bg))
            Text("$rank", style = numberStyle.copy(color = Color(0xFF6E6E6E), drawStyle = Stroke(width = 6f)))
        }
        FocusBox(
            onClick = onClick, onFocus = onFocus, focusRequester = focusRequester,
            shape = RoundedCornerShape(4.dp), focusedScale = 1.08f,
            modifier = Modifier.align(Alignment.BottomEnd).width(129.dp).aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize().background(Palette.Card)) {
                AsyncImage(
                    model = Tmdb.img(t.poster, "w342"), contentDescription = t.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Thẻ ngang "Tiếp tục xem" có thanh tiến độ đỏ. */
@Composable
fun ContinueCard(
    p: WatchProgress,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick, onFocus = onFocus, focusRequester = focusRequester,
        shape = RoundedCornerShape(5.dp), focusedScale = 1.1f,
        modifier = Modifier.width(268.dp).aspectRatio(16f / 9f),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Card)) {
            AsyncImage(
                model = Tmdb.img(p.backdrop ?: p.poster, "w780"),
                contentDescription = p.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xEE000000)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.subtitle, fontSize = 12.sp, color = Palette.Gray, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                ProgressBar(if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x66FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Palette.Red))
    }
}

/** Nút kiểu Netflix: trắng khi được chọn, xám trong suốt khi không. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier,
        onFocus = onFocus,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1.05f,
        showBorder = false,
    ) { focused ->
        val bg = when {
            focused -> Color.White
            primary -> Palette.Red
            else -> Color(0x55808080)
        }
        Text(
            text,
            modifier = Modifier.background(bg).padding(horizontal = 26.dp, vertical = 11.dp),
            color = if (focused) Color.Black else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Chip nhỏ chọn mùa, chất lượng… */
@Composable
fun TvChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick, onFocus = onFocus, focusRequester = focusRequester,
        shape = RoundedCornerShape(50), focusedScale = 1.06f, showBorder = false,
    ) { focused ->
        Text(
            text,
            modifier = Modifier
                .background(if (focused) Color.White else if (selected) Color(0xFF505050) else Color(0x33FFFFFF))
                .padding(horizontal = 18.dp, vertical = 7.dp),
            color = if (focused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun Badge(text: String, color: Color = Color(0x33FFFFFF), textColor: Color = Color.White) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
        color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun LoadingView(message: String = "Đang tải…") {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Palette.Red, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Palette.Gray, fontSize = 16.sp)
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    retryLabel: String = "Thử lại",
) {
    val fr = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Có lỗi", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Palette.Gray, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(0.7f))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onRetry != null) TvButton(retryLabel, onRetry, primary = true, focusRequester = fr)
            if (onBack != null) TvButton("Quay lại", onBack, focusRequester = if (onRetry == null) fr else null)
        }
    }
    AutoFocus(fr)
}

/** Đặt focus cho một phần tử ngay sau khi hiển thị. */
@Composable
fun AutoFocus(fr: FocusRequester, key: Any? = Unit) {
    androidx.compose.runtime.LaunchedEffect(key) {
        for (wait in longArrayOf(80, 200)) {
            kotlinx.coroutines.delay(wait)
            try { fr.requestFocus() } catch (_: Exception) {}
        }
    }
}
