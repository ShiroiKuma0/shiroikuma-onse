package shiroikuma.onse

import android.app.Application
import shiroikuma.onse.ui.UiStore
import shiroikuma.onse.voice.VoiceSettingsStore

class OnseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UiStore.init(this)
        VoiceSettingsStore.init(this)
    }
}
