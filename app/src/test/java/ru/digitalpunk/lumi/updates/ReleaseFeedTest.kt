package ru.digitalpunk.lumi.updates

import ru.digitalpunk.lumi.about.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseFeedTest {
    private val base = "https://github.com/rstrlnkv/lumi/releases/tag/"

    private fun release(tag: String, draft: Boolean = false, url: String = base + tag) =
        """{"tag_name":"$tag","html_url":"$url","draft":$draft}"""

    private fun feed(vararg items: String) = items.joinToString(",", "[", "]")

    private val installed = installedTag("0.1.0", Channel.DEV, 3613543)!!

    @Test fun `release tags are read with their page`() {
        val list = parseReleases(feed(release("v0.1.0.DEV.3613600"), release("v0.1.0")))
        assertEquals(listOf("v0.1.0.DEV.3613600", "v0.1.0"), list.map { it.name })
        assertEquals(base + "v0.1.0", list[1].url)
    }

    @Test fun `drafts and unknown tags are skipped`() =
        assertEquals(emptyList(), parseReleases(feed(release("v0.2.0.DEV.1", draft = true), release("v0.1.0-dev.1"), release("nightly"))))

    @Test fun `a page outside the project's releases is never kept`() {
        listOf(
            "https://evil.example/rstrlnkv/lumi/releases/tag/v0.2.0",
            "http://github.com/rstrlnkv/lumi/releases/tag/v0.2.0",
            "https://github.com/rstrlnkv/lumi.evil/releases/tag/v0.2.0",
            "https://github.com/other/lumi/releases/tag/v0.2.0",
            "https://github.com/rstrlnkv/lumi/releases/tag/v0.2.0\nX",
        ).forEach { assertEquals(emptyList(), parseReleases(feed(release("v0.2.0", url = it.replace("\n", "\\n")))), it) }
    }

    @Test fun `a malformed feed is an empty list`() {
        listOf("", "{}", "[null]", "[1]", "[{}]", "not json", """[{"tag_name":null,"html_url":null}]""").forEach {
            assertEquals(emptyList(), parseReleases(it), it)
        }
    }

    @Test fun `the installed build is not an update to itself`() =
        assertNull(newestUpdate(parseReleases(feed(release("v0.1.0.DEV.3613543"))), installed, Channel.DEV))

    @Test fun `the newest newer build of the channel is the update`() {
        val list = parseReleases(feed(release("v0.1.0.DEV.3613600"), release("v0.1.0.DEV.3613700"), release("v0.1.0.DEV.3613000")))
        assertEquals("v0.1.0.DEV.3613700", newestUpdate(list, installed, Channel.DEV)?.name)
    }

    @Test fun `builds of a less stable channel are not offered`() {
        val list = parseReleases(feed(release("v0.2.0.DEV.3700000"), release("v0.2.0.BETA.3690000")))
        assertNull(newestUpdate(list, installed, Channel.STABLE))
        assertEquals("v0.2.0.BETA.3690000", newestUpdate(list, installed, Channel.BETA)?.name)
    }

    @Test fun `an older build is never offered`() =
        assertTrue(newestUpdate(parseReleases(feed(release("v0.0.9"), release("v0.1.0.DEV.3600000"))), installed, Channel.DEV) == null)
}
