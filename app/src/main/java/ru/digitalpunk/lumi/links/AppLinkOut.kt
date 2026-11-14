package ru.digitalpunk.lumi.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.digitalpunk.lumi.engine.webLink
import java.net.URISyntaxException

/**
 * The intent that opens [link] (an app link from the engine) in another app, cut down as Chrome does it: only a
 * browsable activity, never a named component, a selector or a URI permission. Null when the link cannot be read or
 * names [ownPackage].
 */
internal fun appIntent(link: String, ownPackage: String): Intent? {
    val intent = try {
        if (link.startsWith("intent:", ignoreCase = true)) Intent.parseUri(link, Intent.URI_INTENT_SCHEME)
        else Intent(Intent.ACTION_VIEW, Uri.parse(link))
    } catch (e: URISyntaxException) {
        return null
    }
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.flags = 0
    return intent.takeIf { it.`package` != ownPackage }
}

/** The web page an `intent:` link names for when no app takes it, if it is http or https. */
internal fun Intent.fallbackUrl(): String? = webLink(getStringExtra("browser_fallback_url"))

/** Starts [intent]; false when no app takes it or the app refuses Lumi. */
internal fun Context.startApp(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
