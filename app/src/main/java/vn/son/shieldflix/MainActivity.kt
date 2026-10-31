package vn.son.shieldflix

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import vn.son.shieldflix.ui.AppRoot
import vn.son.shieldflix.ui.ShieldTheme

class MainActivity : ComponentActivity() {

    /** Màn hình phát đặt hàm này để bắt phím remote trước khi PlayerView nhận. */
    var keyInterceptor: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShieldTheme {
                AppRoot()
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.dataString ?: return
        if (data.startsWith("magnet:")) App.instance.incomingMagnets.trySend(data)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyInterceptor?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}
