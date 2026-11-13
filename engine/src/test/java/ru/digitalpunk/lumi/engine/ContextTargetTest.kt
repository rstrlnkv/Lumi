package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextTargetTest {
    private val tab = TabId("t")
    private val page = "https://www.example.org/page"

    private fun press(
        linkUri: String? = null, isImage: Boolean = false, srcUri: String? = null, altText: String? = null,
        title: String? = null, linkText: String? = null, private: Boolean = false, pageUrl: String = page,
    ) = contextTarget(tab, private, pageUrl, linkUri, title, linkText, if (isImage) ContextElement.TYPE_IMAGE else ContextElement.TYPE_NONE, srcUri, altText)

    @Test fun linkOnlyGivesTheSameLinkTargetAsBeforeAndNoImage() {
        val target = press(linkUri = "https://x/a", title = "T")!!
        assertEquals(LinkTarget(tab, "https://x/a", "T", false), target.link)
        assertNull(target.image)
    }

    @Test fun imageOnlyGivesNoLink() {
        val target = press(isImage = true, srcUri = "https://x/a.png")!!
        assertNull(target.link)
        assertEquals(ImageTarget("https://x/a.png", "a.png", "example.org", false), target.image)
    }

    @Test fun imageInsideALinkGivesBoth() {
        val target = press(linkUri = "https://x/a", isImage = true, srcUri = "https://x/b.jpg", altText = "Cat")!!
        assertEquals("https://x/a", target.link?.url)
        assertEquals("https://x/b.jpg", target.image?.url)
    }

    @Test fun nonWebImageSourcesGiveNoImage() {
        listOf("data:image/png;base64,iVBORw0KGgo=", "blob:https://x/1", "file:///a.png", "about:blank", "javascript:1", "ftp://x/a.png", null, "", "   ")
            .forEach { assertNull(press(linkUri = "https://x/a", isImage = true, srcUri = it)?.image, "$it") }
    }

    @Test fun dataImageWithoutALinkGivesNothing() = assertNull(press(isImage = true, srcUri = "data:image/png;base64,AAAA"))

    @Test fun blobImageInsideALinkGivesTheLinkOnly() {
        val target = press(linkUri = "https://x/a", isImage = true, srcUri = "blob:https://x/1")!!
        assertEquals("https://x/a", target.link?.url)
        assertNull(target.image)
    }

    @Test fun notAnImageIgnoresTheSource() = assertNull(press(isImage = false, srcUri = "https://x/v.mp4"))

    @Test fun schemeCaseAndSurroundingSpacesAreAccepted() =
        assertEquals("HTTPS://X/a.PNG", press(isImage = true, srcUri = "  HTTPS://X/a.PNG ")?.image?.url)

    @Test fun imageUrlLengthLimitIsTheLinkLimit() {
        val base = "https://x/"
        assertEquals(MAX_LINK_LENGTH, press(isImage = true, srcUri = base + "a".repeat(MAX_LINK_LENGTH - base.length))?.image?.url?.length)
        assertNull(press(isImage = true, srcUri = base + "a".repeat(MAX_LINK_LENGTH - base.length + 1)))
    }

    @Test fun imageTitleIsAltThenTitleThenFileNameThenNull() {
        fun title(alt: String?, attr: String?, src: String = "https://x/p/cat.jpg?w=1") = press(isImage = true, srcUri = src, altText = alt, title = attr)?.image?.title
        assertEquals("Alt", title("Alt", "Title"))
        assertEquals("Title", title("  ", "Title"))
        assertEquals("cat.jpg", title(null, null))
        assertNull(title(null, null, "https://x/"))
    }

    @Test fun longAltIsCutWithoutSplittingASurrogatePair() {
        val alt = "a".repeat(MAX_LINK_TITLE - 1) + "😀".repeat(150)
        val title = press(isImage = true, srcUri = "https://x/a.png", altText = alt)?.image?.title!!
        assertEquals(MAX_LINK_TITLE - 1, title.length)
    }

    @Test fun siteIsThePageKey() {
        assertEquals("example.org", press(isImage = true, srcUri = "https://cdn/a.png")?.image?.site)
        assertNull(press(isImage = true, srcUri = "https://cdn/a.png", pageUrl = "about:blank")?.image?.site)
    }

    @Test fun privateFlagIsCarriedIntoTheTargetAndTheLink() {
        val target = press(linkUri = "https://x/a", isImage = true, srcUri = "https://x/b.png", private = true)!!
        assertEquals(true, target.inPrivateTab)
        assertEquals(true, target.link?.inPrivateTab)
        assertEquals(true, target.image?.inPrivateTab)
    }

    @Test fun onlyTheImageTypeCountsAsAnImageNotVideoOrAudio() {
        fun image(type: Int) = contextTarget(tab, false, page, "https://x/a", null, null, type, "https://x/m.png", null)?.image
        assertEquals("https://x/m.png", image(ContextElement.TYPE_IMAGE)?.url)
        listOf(ContextElement.TYPE_NONE, ContextElement.TYPE_VIDEO, ContextElement.TYPE_AUDIO).forEach { assertNull(image(it), "$it") }
    }

    @Test fun bidiAndFormatCharactersAreDroppedFromTheImageTitleButZwnjStays() {
        val title = press(isImage = true, srcUri = "https://x/a.png", altText = "\u202Egro.knab\u202C \u200Bpay\u200C\uDB40\uDC41")?.image?.title
        assertEquals("gro.knab pay\u200C", title)
    }

    @Test fun imageInsideAJavascriptOrMailtoLinkGivesTheImageOnly() {
        listOf("javascript:void(0)", "mailto:a@b.c", "#", "").forEach {
            val target = press(linkUri = it, isImage = true, srcUri = "https://x/b.png")!!
            assertNull(target.link, it)
            assertEquals("https://x/b.png", target.image?.url, it)
        }
    }

    @Test fun anImageUrlWithNothingAfterTheSchemeGivesNothing() {
        assertNull(press(isImage = true, srcUri = "https://"))
        assertNull(press(isImage = true, srcUri = "http://"))
    }

    @Test fun imageFileNameIsDecodedAndCutAtQueryAndFragment() {
        assertEquals("my cat.png", press(isImage = true, srcUri = "https://x/a/my%20cat.png?w=1#f")?.image?.title)
    }

    @Test fun aNonImageWithALinkKeepsTheLinkAndNoImageEvenWithAWebSource() {
        val target = press(linkUri = "https://x/a", isImage = false, srcUri = "https://x/v.mp4")!!
        assertEquals("https://x/a", target.link?.url)
        assertNull(target.image)
    }
}
