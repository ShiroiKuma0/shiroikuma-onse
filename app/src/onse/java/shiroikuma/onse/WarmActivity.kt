package shiroikuma.onse

import android.app.Activity
import android.os.Bundle

/**
 * An invisible, exported activity a sister app may start to bring 音声's process up before its
 * first render request (Theme.NoDisplay; finishes in onCreate, so nothing is shown). The data
 * door's `describe` call wakes the process too, and is the path 自由作業盤 measured (≈70 ms).
 */
class WarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
