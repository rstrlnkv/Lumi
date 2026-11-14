package ru.digitalpunk.lumi.net

import ru.digitalpunk.lumi.data.icons.IconCandidate
import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import ru.digitalpunk.lumi.data.icons.PageIconOffer
import ru.digitalpunk.lumi.engine.PageIconLink
import ru.digitalpunk.lumi.engine.PageIcons
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageIconOffersTest {
    @Test fun touchIconAndSvgLinksMapToCandidatesAndAnSvgHasNoSize() = assertEquals(
        PageIconOffer(
            "https://a.com",
            listOf(
                IconCandidate("https://a.com/t.png", Kind.Touch, 180),
                IconCandidate("https://a.com/i", Kind.Icon, null, svg = true),
                IconCandidate("https://a.com/v.SVG?x=1", Kind.Icon, null, svg = true),
                IconCandidate("https://a.com/f.ico", Kind.Icon, 32),
            ),
            "https://a.com/m.json",
            0xFF336699.toInt(),
        ),
        pageIconOffer(PageIcons(
            "https://a.com",
            listOf(
                PageIconLink("https://a.com/t.png", true, 180, null),
                PageIconLink("https://a.com/i", false, 512, "image/svg+xml"),
                PageIconLink("https://a.com/v.SVG?x=1", false, null, null),
                PageIconLink("https://a.com/f.ico", false, 32, "image/x-icon"),
            ),
            "https://a.com/m.json",
            0xFF336699.toInt(),
        )),
    )

    // Page links reach the icons only through the engine listener, and only for hosts the person bookmarked.
    @Test fun theEngineListenerOffersPageLinksAndTheWantedHostsFollowTheBookmarks() {
        val app = File("src/main/java/ru/digitalpunk/lumi/LumiApplication.kt").readText()
        assertTrue("it.pageIconListener = { icons -> siteIcons.offerPageLinks(pageIconOffer(icons)) }" in app)
        assertTrue("library.nodes.collect { nodes -> icons.wantedHosts = bookmarkIconHosts(nodes.map { n -> n.url }) }" in app)
    }
}
