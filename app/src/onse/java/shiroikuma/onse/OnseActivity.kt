package shiroikuma.onse

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import shiroikuma.onse.ui.OnseTheme
import shiroikuma.onse.ui.UiPage
import shiroikuma.onse.ui.UiStore

/**
 * The launcher activity (upstream has none): the main screen, and 白い熊 音声 UI on top of it.
 * Also the TTS engine's settings activity (flavour `xml/tts_config`).
 */
class OnseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            OnseTheme {
                val p by UiStore.prefs.collectAsState()
                var uiPage by rememberSaveable { mutableStateOf(intent?.getBooleanExtra(EXTRA_OPEN_UI, false) == true) }
                BackHandler(enabled = uiPage) { uiPage = false }
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(p.background)).safeDrawingPadding()) {
                    if (uiPage) UiPage(version, onBack = { uiPage = false })
                    else MainScreen(version, onOpenUi = { uiPage = true })
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_UI = "shiroikuma.onse.extra.OPEN_UI"
    }
}
