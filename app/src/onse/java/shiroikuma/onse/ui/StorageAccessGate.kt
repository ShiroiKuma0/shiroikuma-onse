package shiroikuma.onse.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
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
 * Two checks every time the app comes to the foreground (白い熊, 2026-09-30), asked one after the
 * other:
 *
 * 1. **All-files access** — the render service writes audio where sister apps ask, and backups go
 *    to a shared folder.
 * 2. **Battery-optimisation exemption** — measured on the Mate XT (自由作業盤, 2026-09-30): without
 *    it EMUI refuses the render service's foreground start when a sister app wakes 音声 from the
 *    background (`ERROR:no-foreground-start`); with it a cold render works.
 *
 * "Later" dismisses a request only until the next time the app is entered.
 */
@Composable
fun StorageAccessGate() {
    val context = LocalContext.current
    var storageMissing by remember { mutableStateOf(false) }
    var batteryMissing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            storageMissing = !Environment.isExternalStorageManager()
            batteryMissing = !isBatteryExempt(context)
        }
    }
    when {
        storageMissing -> GateDialog(
            title = "All-files access needed",
            body = "白い熊 音声 writes the audio it renders for sister apps (言語島) to the paths they name, " +
                "and its backups to a shared folder. Android only allows that with “All files access”.\n\n" +
                "Grant it on the next screen, then come back.",
            onLater = { storageMissing = false },
            onGrant = { requestAllFilesAccess(context) },
        )
        batteryMissing -> GateDialog(
            title = "Battery optimisation must be off",
            body = "Sister apps (白い熊 自由作業盤's 言語島) start 白い熊 音声's render service from the " +
                "background. While 音声 is battery-optimised, the phone refuses that start and the " +
                "render fails.\n\n" +
                "Tap “Allow” and confirm “Don't optimise” / “Allow”. If a list opens instead, choose " +
                "“All apps”, find 白い熊 音声 and set it to “Don't allow” optimisation.\n\n" +
                "On this Huawei phone also open Settings → Battery → App launch, find 白い熊 音声, " +
                "switch it to “Manage manually” and turn on Auto-launch, Secondary launch and Run in background.",
            onLater = { batteryMissing = false },
            onGrant = { requestBatteryExemption(context) },
            grantLabel = "Allow",
        )
    }
}

@Composable
private fun GateDialog(title: String, body: String, onLater: () -> Unit, onGrant: () -> Unit, grantLabel: String = "Grant access") {
    HouseDialog(onDismiss = onLater, dismissable = false) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 16.dp)) {
            DialogTitle(title, warn = true)
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                Pill("Later", onClick = onLater)
                Spacer(Modifier.width(10.dp))
                Pill(grantLabel, onClick = onGrant)
            }
        }
    }
}

fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

@SuppressLint("BatteryLife") // the exemption is the documented fix for the sister-app render start
fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }.recoverCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
