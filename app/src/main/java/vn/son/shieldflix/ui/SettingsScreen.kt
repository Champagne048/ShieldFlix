package vn.son.shieldflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.son.shieldflix.App
import vn.son.shieldflix.data.formatSize
import vn.son.shieldflix.web.ConfigServer

@Composable
fun SettingsScreen(nav: Nav) {
    val app = App.instance
    val p = app.prefs
    val version by p.version.collectAsState()
    val ip = remember { ConfigServer.lanAddress() ?: "<IP Shield>" }
    val scope = rememberCoroutineScope()
    var cache by remember { mutableLongStateOf(-1L) }
    val firstFr = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cache = withContext(Dispatchers.IO) { app.engine.cacheBytes() }
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(nav, current = "settings")
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp)) {
            // Cột trái: cấu hình qua điện thoại + trạng thái
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Cấu hình nhanh bằng điện thoại", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Mở trình duyệt trong cùng mạng Wi-Fi:", color = Palette.Gray, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "http://$ip:${ConfigServer.PORT}",
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Mã PIN: ${p.pin}", color = Palette.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (app.configServer == null) Text("Không mở được cổng ${ConfigServer.PORT}", color = Palette.Red)
                Spacer(Modifier.height(24.dp))

                Text("Trạng thái", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (version >= 0) {
                    StatusLine("Tiếng Việt cho tên phim (TMDB, không bắt buộc)", p.hasTmdb)
                    StatusLine("Nguồn torrent: ${p.torznabUrls.size} nguồn Torznab", p.hasSources)
                    StatusLine("Phụ đề Subdl", p.subdlKey.isNotBlank())
                    StatusLine("Phụ đề OpenSubtitles" + if (p.osUser.isNotBlank()) " (đã đăng nhập)" else "", p.osApiKey.isNotBlank())
                }
                Spacer(Modifier.height(24.dp))

                Text("Tuỳ chọn", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (version >= 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionRow("Chất lượng ưu tiên", p.preferredQuality, focusRequester = firstFr) {
                            p.preferredQuality = when (p.preferredQuality) { "1080p" -> "2160p"; "2160p" -> "720p"; else -> "1080p" }
                        }
                        OptionRow("Ẩn bản quay rạp (CAM/TS)", if (p.hideCam) "Bật" else "Tắt") { p.hideCam = !p.hideCam }
                        OptionRow("Xoá file sau khi xem xong", if (p.deleteAfterWatch) "Bật" else "Tắt") { p.deleteAfterWatch = !p.deleteAfterWatch }
                        OptionRow("Cỡ chữ phụ đề", "${p.subtitleSizeSp}") {
                            p.subtitleSizeSp = if (p.subtitleSizeSp >= 44) 22 else p.subtitleSizeSp + 4
                        }
                        OptionRow("Nền tối sau phụ đề", if (p.subtitleBackground) "Bật" else "Tắt") { p.subtitleBackground = !p.subtitleBackground }
                        OptionRow("Xoá bộ nhớ đệm torrent", if (cache < 0) "…" else formatSize(cache)) {
                            scope.launch {
                                withContext(Dispatchers.IO) { app.engine.clearAll() }
                                cache = 0
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(40.dp))
            // Cột phải: nhập trực tiếp trên TV (dự phòng khi không dùng điện thoại)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Hoặc nhập trên TV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                KeyField("Khoá TMDB", p.tmdbKey) { p.tmdbKey = it }
                KeyField("URL Torznab (nhiều nguồn: cách nhau bằng dấu ;)", p.torznabUrls.joinToString(";")) {
                    p.torznabUrls = it.split(';', '\n')
                }
                KeyField("Khoá Subdl", p.subdlKey) { p.subdlKey = it }
                KeyField("Khoá OpenSubtitles", p.osApiKey) { p.osApiKey = it }
                KeyField("Tài khoản OpenSubtitles", p.osUser) { p.osUser = it }
                KeyField("Mật khẩu OpenSubtitles", p.osPass, password = true) { p.osPass = it }
            }
        }
    }
    AutoFocus(firstFr)
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(if (ok) "✓" else "✗", color = if (ok) Palette.Green else Palette.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (ok) Color.White else Palette.Gray, fontSize = 16.sp)
    }
}

@Composable
private fun OptionRow(label: String, value: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) Color.White else Palette.Card).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (f) Color.Black else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = if (f) Color.Black else Palette.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KeyField(label: String, initial: String, password: Boolean = false, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSave(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color(0xFF555555),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Palette.Gray,
            cursorColor = Palette.Red,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}
