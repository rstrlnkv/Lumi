package ru.digitalpunk.lumi.updates

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.digitalpunk.lumi.about.Channel

/** The page of every release Lumi may open: it goes into a tab, so nothing else from the feed is trusted. */
private val RELEASE_PAGE = Regex("""https://github\.com/rstrlnkv/lumi/releases/[A-Za-z0-9._/-]+""")

/** A published release: its tag, the tag's name and its page on GitHub. */
data class Release(val tag: ReleaseTag, val name: String, val url: String)

/** GitHub's release list; drafts, unknown tags and pages outside the project are dropped, anything malformed is empty. */
fun parseReleases(json: String): List<Release> = try {
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { i ->
        val item = array.opt(i) as? JSONObject ?: return@mapNotNull null
        if (item.opt("draft") == true) return@mapNotNull null
        val name = item.opt("tag_name") as? String ?: return@mapNotNull null
        val url = (item.opt("html_url") as? String)?.takeIf { RELEASE_PAGE.matches(it) } ?: return@mapNotNull null
        parseTag(name)?.let { Release(it, name, url) }
    }
} catch (_: JSONException) {
    emptyList()
}

/** The newest release of [channel] above the installed build, or null. */
fun newestUpdate(releases: List<Release>, installed: ReleaseTag, channel: Channel): Release? =
    releases.filter { channel.sees(it.tag.channel) && it.tag > installed }.maxByOrNull { it.tag }
