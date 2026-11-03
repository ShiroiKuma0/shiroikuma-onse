package shiroikuma.onse

import android.graphics.Color
import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import shiroikuma.onse.ui.OnseTheme
import shiroikuma.onse.ui.StorageAccessGate
import shiroikuma.onse.ui.UiPage
import shiroikuma.onse.ui.UiStore

/**
 * The launcher activity (upstream has none): the main screen, and 白い熊 音声 UI on top of it.
 * Also the TTS engine's settings activity (flavour `xml/tts_config`).
 */
class OnseActivity : ComponentActivity() {
    // 白い熊 音声 UI over the main screen; text arriving from another app closes it again.
    private var uiPage by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        uiPage = savedInstanceState?.getBoolean(STATE_UI_PAGE) ?: (intent?.getBooleanExtra(EXTRA_OPEN_UI, false) == true)
        if (savedInstanceState == null) receiveText(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            OnseTheme {
                val p by UiStore.prefs.collectAsState()
                BackHandler(enabled = uiPage) { uiPage = false }
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(p.background)).safeDrawingPadding()) {
                    if (uiPage) UiPage(version, onBack = { uiPage = false })
                    else MainScreen(version, onOpenUi = { uiPage = true })
                }
                StorageAccessGate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UI, false)) uiPage = true
        receiveText(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_UI_PAGE, uiPage)
    }

    /**
     * Share → 白い熊 音声 (`SEND` text/plain) and the text-selection menu's 白い熊 音声
     * (`PROCESS_TEXT`): the text goes into the dashboard of its language's tab, ready to Generate.
     */
    private fun receiveText(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }?.toString()?.takeIf { it.isNotBlank() } ?: return
        uiPage = false
        shiroikuma.onse.production.Production.receive(text)
    }

    companion object {
        private const val STATE_UI_PAGE = "uiPage"
        const val EXTRA_OPEN_UI = "shiroikuma.onse.extra.OPEN_UI"
    }
}
