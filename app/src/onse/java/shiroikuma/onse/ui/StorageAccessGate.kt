package shiroikuma.onse.ui

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * All-files access is checked every time the app comes to the foreground (白い熊, 2026-09-30):
 * the render service writes the audio files where sister apps ask, and backups go to a shared
 * directory — neither works without it. When it is missing this asks for it at once; "Later"
 * only dismisses it until the next time the app is entered.
 */
@Composable
fun StorageAccessGate() {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            missing = !Environment.isExternalStorageManager()
        }
    }
    if (!missing) return
    HouseDialog(onDismiss = { missing = false }, dismissable = false) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 16.dp)) {
            DialogTitle("All-files access needed", warn = true)
            Text(
                "白い熊 音声 writes the audio it renders for sister apps (言語島) to the paths they name, " +
                    "and its backups to a shared folder. Android only allows that with “All files access”.\n\n" +
                    "Grant it on the next screen, then come back.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                Pill("Later") { missing = false }
                Spacer(Modifier.width(10.dp))
                Pill("Grant access") { requestAllFilesAccess(context) }
            }
        }
    }
}
