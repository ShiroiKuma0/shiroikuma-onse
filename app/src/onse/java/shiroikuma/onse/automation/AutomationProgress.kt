/*
 * 白い熊 音声 (shiroikuma-onse) fork: the ONE 保存復元 progress broadcast, for both the §1
 * receiver path and the §2a data door. Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.content.Context
import android.content.Intent
import dev.ztssst.voicevox_tts.R
import shiroikuma.onse.ui.Backup

/**
 * Progress with **real numbers, never a percentage**. [correlationId] is the `reply_id` on the §1
 * path and the `job_id` on the data door; it goes out as both extras. Every broadcast carries
 * `setPackage` — without it an implicit broadcast reaches no manifest receiver since API 26.
 */
object AutomationProgress {

    fun send(
        context: Context,
        progressAction: String?,
        replyPackage: String?,
        correlationId: String,
        cat: Backup.Cat,
        position: Int,
        total: Int,
        bytes: Long? = null,
    ) {
        if (progressAction.isNullOrBlank() || replyPackage.isNullOrBlank()) return
        context.sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, correlationId)
                putExtra(AutomationProvider.KEY_JOB_ID, correlationId)
                putExtra("app", context.getString(R.string.app_name))
                putExtra("item", cat.id)
                putExtra("text", "区分 $position/$total — ${cat.label}")
                // Counting categories: `current` is the POSITION of the one being written.
                putExtra("current", position.toLong())
                putExtra("total", total.toLong())
                putExtra("unit", "区分")
                bytes?.let { putExtra("bytes", it) }
            },
        )
    }
}
