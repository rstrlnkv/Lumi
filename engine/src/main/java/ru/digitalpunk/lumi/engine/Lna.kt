package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import java.net.URI
import java.util.Base64

/** Local network access (ADR 015): [Network] reaches the LAN, [Device] reaches other apps on this phone (loopback). */
enum class LnaKind { Network, Device }

enum class LnaAnswer { Allow, Block, Dismiss }

/** Requests with the same key share one sheet and one answer; [via] keeps a third-party frame's request apart. */
data class LnaKey(val uri: String, val kind: LnaKind, val private: Boolean, val via: String?)

/** The sheet's content: [site] wants to do [LnaKey.kind]; [via] is a third-party frame's host when it differs. */
data class LnaPrompt(val key: LnaKey, val site: String, val via: String?)

const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

internal fun lnaKind(permission: Int): LnaKind? = when (permission) {
    PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> LnaKind.Network
    PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> LnaKind.Device
    else -> null
}

internal fun lnaValue(answer: LnaAnswer): Int = when (answer) {
    LnaAnswer.Allow -> ContentPermission.VALUE_ALLOW
    LnaAnswer.Block -> ContentPermission.VALUE_DENY
    // Gecko stores nothing for PROMPT: the site can ask again.
    LnaAnswer.Dismiss -> ContentPermission.VALUE_PROMPT
}

/** A denial GeckoView stored while Lumi had no permission delegate; private permissions end with the session anyway. */
internal fun isStoredLnaDenial(permission: Int, value: Int, privateMode: Boolean): Boolean =
    !privateMode && lnaKind(permission) != null && value == ContentPermission.VALUE_DENY

/** A site's stored local network answer, for Settings; [allowed] only when every stored answer of the site allows. */
data class LnaSite(val site: String, val via: String?, val allowed: Boolean)

/** The parts of a Gecko `ContentPermission` the Settings list needs. */
internal data class LnaEntry(val uri: String, val thirdPartyOrigin: String?, val permission: Int, val value: Int, val privateMode: Boolean)

/** An Allow or Block of a normal tab; private answers end with the private session and are not listed. */
internal fun LnaEntry.isStoredAnswer(): Boolean =
    !privateMode && lnaKind(permission) != null && (value == ContentPermission.VALUE_ALLOW || value == ContentPermission.VALUE_DENY)

/** The site and third-party frame host, as the sheet shows them. */
internal fun LnaEntry.siteAndVia(): Pair<String, String?> = lnaPrompt(uri, thirdPartyOrigin, LnaKind.Network, false).let { it.site to it.via }

/** One row per site and frame host, both kinds together, sorted by site. */
internal fun lnaSites(entries: List<LnaEntry>): List<LnaSite> =
    entries.filter { it.isStoredAnswer() }.groupBy { it.siteAndVia() }
        .map { (key, answers) -> LnaSite(key.first, key.second, answers.all { it.value == ContentPermission.VALUE_ALLOW }) }
        .sortedWith(compareBy<LnaSite> { it.site }.thenBy { it.via.orEmpty() })

/** The longest DNS name; a longer host or a whole-URI fallback is cut for the sheet. */
internal const val MAX_LNA_HOST_LENGTH = 253

/** The host for the sheet, without bidi, format or control characters a page could use to fake another name. */
private fun hostOf(uri: String): String {
    val clean = uri.filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }
    return (runCatching { URI(clean).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: clean).take(MAX_LNA_HOST_LENGTH)
}

internal fun lnaPrompt(uri: String, thirdPartyOrigin: String?, kind: LnaKind, private: Boolean): LnaPrompt {
    val site = hostOf(uri)
    val via = thirdPartyOrigin?.takeIf { it.isNotEmpty() }?.let(::hostOf)?.takeIf { it != site }
    return LnaPrompt(LnaKey(uri, kind, private, via), site, via)
}

/** Only Gecko's ask for the Android 17 local network permission goes to the app; any other ask is refused as before. */
internal fun asksOnlyLocalNetwork(permissions: Array<String>?): Boolean =
    !permissions.isNullOrEmpty() && permissions.all { it == ACCESS_LOCAL_NETWORK }

/** Base64, so no character of the page can end or change the URL. */
internal fun htmlDataUrl(html: String): String =
    "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))

/** Requests waiting for an answer, merged per [LnaKey], in arrival order; [head] is the one on screen. */
internal class LnaQueue<H> {
    private class Entry<H>(val prompt: LnaPrompt, val waiting: MutableList<Pair<TabId, H>>)

    private val entries = LinkedHashMap<LnaKey, Entry<H>>()

    val head: LnaPrompt? get() = entries.values.firstOrNull()?.prompt

    fun offer(tab: TabId, prompt: LnaPrompt, handle: H) {
        entries.getOrPut(prompt.key) { Entry(prompt, mutableListOf()) }.waiting += tab to handle
    }

    /** Takes the key out; every request under it gets the answer. */
    fun answer(key: LnaKey): List<H> = entries.remove(key)?.waiting?.map { it.second }.orEmpty()

    /** Takes out [tab]'s requests (it navigated or closed); a key left with none goes too. */
    fun cancelTab(tab: TabId): List<H> {
        val out = mutableListOf<H>()
        entries.values.forEach { e -> e.waiting.removeAll { (t, h) -> (t == tab).also { if (it) out += h } } }
        entries.values.removeAll { it.waiting.isEmpty() }
        return out
    }
}
