package ru.digitalpunk.lumi.updates

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.digitalpunk.lumi.about.Channel

/** The only page Lumi opens for a release: it goes into a tab, so it must be exactly the page of the release's own tag. */
private const val RELEASE_PAGE = "https://github.com/rstrlnkv/lumi/releases/tag/"

/** A published release: its tag, the tag's name and its page on GitHub. */
data class Release(val tag: ReleaseTag, val name: String, val url: String)

/** GitHub's release list; drafts, unknown tags and pages outside the project are dropped. Null when it is not a JSON array. */
fun parseReleases(json: String): List<Release>? = try {
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { i ->
        val item = array.opt(i) as? JSONObject ?: return@mapNotNull null
        if (item.opt("draft") == true) return@mapNotNull null
        val name = item.opt("tag_name") as? String ?: return@mapNotNull null
        val tag = parseTag(name) ?: return@mapNotNull null
        val url = (item.opt("html_url") as? String)?.takeIf { it == RELEASE_PAGE + name } ?: return@mapNotNull null
        Release(tag, name, url)
    }
} catch (_: JSONException) {
    null
} catch (_: StackOverflowError) {
    // Android's org.json parses nesting by recursion: a body of a million '[' must not end the process.
    null
}

/** The newest release of [channel] above the installed build, or null. */
fun newestUpdate(releases: List<Release>, installed: ReleaseTag, channel: Channel): Release? =
    releases.filter { channel.sees(it.tag.channel) && it.tag > installed }.maxByOrNull { it.tag }
