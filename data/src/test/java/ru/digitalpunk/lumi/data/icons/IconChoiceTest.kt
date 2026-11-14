package ru.digitalpunk.lumi.data.icons

import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconChoiceTest {
    private val root = "https://a.com/"
    private fun c(name: String, kind: Kind, size: Int?) = IconCandidate("https://a.com/$name", kind, size)
    private fun ranked(vararg cs: IconCandidate) = rankIcons(cs.toList(), root).map { it.url.removePrefix(root) }

    @Test fun aTouchIconOf180BeatsA512ManifestIcon() =
        assertEquals(listOf("t.png", "m.png", "favicon.ico"), ranked(c("m.png", Kind.Manifest, 512), c("t.png", Kind.Touch, 180)))

    @Test fun anIconOf192BeatsA32Favicon() =
        assertEquals(listOf("big.png", "small.png", "favicon.ico"), ranked(c("small.png", Kind.Icon, 32), c("big.png", Kind.Icon, 192)))

    @Test fun aTouchIconWithoutSizesCountsAs180AndMeetsTheTarget() =
        assertEquals(listOf("t.png", "i.png", "favicon.ico"), ranked(c("i.png", Kind.Icon, 96), c("t.png", Kind.Touch, null)))

    @Test fun amongIconsThatMeetTheTargetTheSmallestWins() =
        assertEquals(listOf("i192.png", "i512.png", "favicon.ico"), ranked(c("i512.png", Kind.Icon, 512), c("i192.png", Kind.Icon, 192)))

    @Test fun belowTheTargetTheLargestWins() =
        assertEquals(listOf("i96.png", "i48.png", "favicon.ico"), ranked(c("i48.png", Kind.Icon, 48), c("i96.png", Kind.Icon, 96)))

    @Test fun aSmallTouchIconStillLosesToAnIconThatMeetsTheTarget() =
        assertEquals(listOf("i192.png", "t57.png", "favicon.ico"), ranked(c("t57.png", Kind.Touch, 57), c("i192.png", Kind.Icon, 192)))

    @Test fun atMostFourWithTheFaviconLastAndNoDuplicates() {
        val got = ranked(c("a.png", Kind.Icon, 16), c("a.png", Kind.Icon, 16), c("b.png", Kind.Icon, 17), c("c.png", Kind.Icon, 18), c("d.png", Kind.Icon, 19))
        assertEquals(listOf("d.png", "c.png", "b.png", "favicon.ico"), got)
    }

    @Test fun aListedFaviconIsNotAddedTwice() =
        assertEquals(listOf("favicon.ico"), ranked(c("favicon.ico", Kind.Icon, null)))

    @Test fun tiesKeepDocumentOrder() =
        assertEquals(listOf("one.png", "two.png", "favicon.ico"), ranked(c("one.png", Kind.Touch, 180), c("two.png", Kind.Touch, 180)))

    @Test fun theManifestIsNeededOnlyWhenNoLinkMeetsTheTarget() {
        assertTrue(needsManifest(listOf(c("i.png", Kind.Icon, 32))))
        assertTrue(needsManifest(emptyList()))
        assertFalse(needsManifest(listOf(c("t.png", Kind.Touch, null))))
        assertFalse(needsManifest(listOf(c("i.png", Kind.Icon, 192))))
    }
}
