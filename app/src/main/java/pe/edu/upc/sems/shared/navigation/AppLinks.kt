package pe.edu.upc.sems.shared.navigation

import java.net.URI
import java.net.URLDecoder
import pe.edu.upc.sems.BuildConfig

/**
 * Links that can open the app (see the intent filter in AndroidManifest.xml).
 *
 * They are read once by MainActivity and handed to AppViewModel, instead of being declared as
 * Navigation deep links: every new NavController reads Activity.intent when its graph is set, so the
 * access graph, recreated after each logout, would open the same link again.
 */
object AppLinks {

    private const val RESET_PASSWORD_PATH = "/reset-password"

    /** Token of `https://<web app>/reset-password?token=…`, or null for any other link. */
    fun resetPasswordToken(link: String?, webAppUrl: String = BuildConfig.WEB_APP_URL): String? {
        if (link.isNullOrBlank()) return null
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        val webHost = runCatching { URI(webAppUrl).host }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals(webHost, ignoreCase = true)) return null
        if (uri.path?.trimEnd('/') != RESET_PASSWORD_PATH) return null
        return uri.rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("token=") }
            ?.removePrefix("token=")
            // decode(String, Charset) needs API 33; minSdk is 29.
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
    }
}
