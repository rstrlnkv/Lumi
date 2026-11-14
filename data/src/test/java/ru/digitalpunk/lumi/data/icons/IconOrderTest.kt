package ru.digitalpunk.lumi.data.icons

import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconOrderTest {
    private val root = "https://a.com/"
    private fun c(name: String, kind: Kind, size: Int?, svg: Boolean = false, maskable: Boolean = false) =
        IconCandidate(root + name, kind, size, svg = svg, maskable = maskable)
    private fun order(vararg cs: IconCandidate) = rankIcons(cs.toList(), root).map { it.url.removePrefix(root) }

    @Test fun theRootTouchPathsAreProbedAfterLargeIconsWhenThePageHasNoTouchLink() =
        assertEquals(
            listOf("m512.png", "apple-touch-icon.png", "apple-touch-icon-precomposed.png", "i32.png", "favicon.ico"),
            order(c("i32.png", Kind.Icon, 32), c("m512.png", Kind.Manifest, 512)),
        )

    @Test fun aTouchLinkLeavesTheRootPathsUnprobed() =
        assertEquals(listOf("t.png", "favicon.ico"), order(c("t.png", Kind.Touch, null)))

    @Test fun anSvgComesAfterEveryRasterAndBeforeTheFavicon() =
        assertEquals(
            listOf("apple-touch-icon.png", "apple-touch-icon-precomposed.png", "i16.png", "i.svg", "favicon.ico"),
            order(c("i.svg", Kind.Icon, null, svg = true), c("i16.png", Kind.Icon, 16)),
        )

    @Test fun aMaskableIconComesAfterAnAnyIconOfTheSameSize() {
        assertEquals(listOf("any.png", "mask.png"), order(c("mask.png", Kind.Manifest, 192, maskable = true), c("any.png", Kind.Manifest, 192)).take(2))
        assertEquals(
            listOf("apple-touch-icon.png", "apple-touch-icon-precomposed.png", "any.png", "mask.png"),
            order(c("mask.png", Kind.Manifest, 96, maskable = true), c("any.png", Kind.Manifest, 96)).take(4),
        )
    }

    @Test fun atMostSevenAttemptsTwoProbesThreeRastersOneSvgAndTheFavicon() {
        val many = (1..10).map { c("i$it.png", Kind.Icon, it * 10) } + (1..3).map { c("s$it.svg", Kind.Icon, null, svg = true) }
        val got = rankIcons(many, root)
        assertEquals(7, got.size)
        assertEquals(2, got.count { it.kind == Kind.RootTouch })
        assertEquals(1, got.count { it.svg })
        assertEquals(Kind.Favicon, got.last().kind)
    }

    @Test fun theRootProbesAreFetchedAsTouchIconsOfUnknownSize() =
        assertTrue(rankIcons(emptyList(), root).filter { it.kind == Kind.RootTouch }.all { it.size == null && !it.svg })
}
