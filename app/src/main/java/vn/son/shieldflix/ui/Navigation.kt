package vn.son.shieldflix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import vn.son.shieldflix.App
import vn.son.shieldflix.data.MediaType
import vn.son.shieldflix.data.PlayRequest
import java.net.URLDecoder

sealed class Screen {
    abstract val key: String

    data object Home : Screen() { override val key = "home" }
    data object Search : Screen() { override val key = "search" }
    data object Settings : Screen() { override val key = "settings" }
    data class Detail(val type: MediaType, val id: Int, val imdbId: String? = null) : Screen() {
        override val key = "detail:$type:$id:${imdbId ?: ""}"
    }
    data class Player(val request: PlayRequest, val serial: Long = System.nanoTime()) : Screen() {
        override val key = "player:$serial"
    }
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        // Không chồng hai trình phát lên nhau.
        if (s is Screen.Player && current is Screen.Player) stack[stack.lastIndex] = s else stack.add(s)
    }

    fun replace(s: Screen) {
        stack[stack.lastIndex] = s
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun goHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/** Yêu cầu phát từ một magnet thuần (không có thông tin TMDB). */
fun magnetRequest(magnet: String): PlayRequest {
    val dn = Regex("[?&]dn=([^&]+)").find(magnet)?.groupValues?.get(1)
    val name = dn?.let { try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } } ?: "Magnet"
    return PlayRequest(
        tmdbId = 0, type = MediaType.MOVIE, title = name, originalTitle = name,
        year = "", imdbId = null, magnet = magnet,
    )
}

@Composable
fun AppRoot() {
    val nav = remember { Nav() }
    val holder = rememberSaveableStateHolder()

    LaunchedEffect(Unit) {
        for (m in App.instance.incomingMagnets) nav.push(Screen.Player(magnetRequest(m)))
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    val cur = nav.current
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        holder.SaveableStateProvider(cur.key) {
            when (cur) {
                is Screen.Home -> HomeScreen(nav)
                is Screen.Search -> SearchScreen(nav)
                is Screen.Settings -> SettingsScreen(nav)
                is Screen.Detail -> DetailScreen(nav, cur.type, cur.id, cur.imdbId)
                is Screen.Player -> PlayerScreen(nav, cur.request)
            }
        }
    }
}
