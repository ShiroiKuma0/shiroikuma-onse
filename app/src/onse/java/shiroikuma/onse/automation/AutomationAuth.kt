/*
 * 白い熊 音声 (shiroikuma-onse) fork: the gate for the 保存復元 automation contract (v2).
 * Ported from shiroikuma-jinsoningen.
 */

package shiroikuma.onse.automation

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The external-automation gate: a master switch that ships ON, and a token that is OPTIONAL.
 *
 * - [enabled] defaults to **true** — a clean phone being restored by 応用管理 has had nothing
 *   configured, and a gate that only works once the phone is set up is no gate for setting it up.
 * - [requireToken] defaults to **false**. A token sent to an app that does not require one is
 *   **ignored, never refused** — tokens outlive the settings they were pasted for.
 * - All three writes use `commit()`: the gate fails OPEN, and 応用管理's force-stop (SIGKILL)
 *   after an import leaves an in-flight `apply()` nowhere to land.
 * - Its own preferences file, deliberately in no backup category — a token never travels in a ZIP.
 */
object AutomationAuth {

    private const val PREFS = "onse_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).commit()
    }

    fun requireToken(context: Context): Boolean = prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, value).commit()
    }

    /** The token, minted on first read so the row is never empty. */
    fun token(context: Context): String {
        val stored = prefs(context).getString(KEY_TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_TOKEN, token).commit()
        return token
    }

    /** Constant-time compare. */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** The whole gate in one place: null = proceed, otherwise the exact `ERROR:` line. */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
