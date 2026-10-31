package vn.son.shieldflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.son.shieldflix.App
import vn.son.shieldflix.data.Title

@Composable
fun SearchScreen(nav: Nav) {
    val app = App.instance
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Title>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(600)
        loading = true
        error = null
        try {
            results = app.catalog.search(query)
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(nav, current = "search")
        Column(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Tên phim, series (tiếng Việt hoặc tiếng Anh)…", color = Palette.DimGray) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Palette.Red,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                modifier = Modifier.fillMaxWidth(0.6f).focusRequester(fieldFr),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    loading -> "Đang tìm…"
                    error != null -> error!!
                    query.isNotBlank() && results.isEmpty() -> "Không thấy kết quả"
                    else -> "Bấm OK trên remote để mở bàn phím, hoặc dùng nút micro để nói"
                },
                color = Palette.Gray, fontSize = 14.sp,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { "${it.type}${it.id}" }) { t ->
                PosterCard(t, onClick = { nav.push(Screen.Detail(t.type, t.id, t.imdbId)) }, width = 140.dp)
            }
        }
    }
    AutoFocus(fieldFr)
}
