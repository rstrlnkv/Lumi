package ru.digitalpunk.lumi.data.icons

import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconLinksTest {
    private val base = "https://a.com/"
    private fun ordered(html: String, baseUrl: String = base) = rankIcons(parseIconLinks(html, baseUrl), "https://a.com/").filter { it.kind != IconCandidate.Kind.RootTouch }.map { it.url }

    @Test fun aTouchIconBeatsALargerIcon() = assertEquals(
        listOf("https://a.com/t.png", "https://a.com/big.png", "https://a.com/favicon.ico"),
        ordered("""<link rel="icon" sizes="512x512" href="/big.png"><link rel="apple-touch-icon" sizes="180x180" href="/t.png">"""),
    )

    @Test fun theLargestIconWinsAndUnknownSizesComeAfterKnownOnes() = assertEquals(
        listOf("https://a.com/32.png", "https://a.com/16.png", "https://a.com/any.png", "https://a.com/favicon.ico"),
        ordered("""<link rel=icon sizes=any href=/any.png><link rel="icon" sizes="16x16" href="/16.png"><link rel="icon" sizes="32x32" href="/32.png">"""),
    )

    @Test fun sizesTakeTheLargestEdgeAndAnyOrMissingIsUnknown() {
        val links = parseIconLinks("""<link rel="icon" sizes="16x16 32x32" href="/a.png"><link rel="icon" sizes="any" href="/b.png"><link rel="icon" href="/c.png">""", base)
        assertEquals(listOf(32, null, null), links.map { it.size })
    }

    @Test fun shortcutIconCountsAndMaskIconSvgAndDataAreExcluded() {
        val links = parseIconLinks(
            """<link rel="shortcut icon" href="/s.ico"><link rel="mask-icon" href="/m.png"><link rel="fluid-icon" href="/f.png">
               <link rel="icon" href="/v.svg"><link rel="icon" type="image/svg+xml" href="/v2"><link rel="icon" href="data:image/png;base64,AAAA">""",
            base,
        )
        assertEquals(listOf(IconCandidate("https://a.com/s.ico", Kind.Icon, null)), links.filter { !it.svg })
        assertEquals(listOf("https://a.com/v.svg", "https://a.com/v2"), links.filter { it.svg }.map { it.url })
    }

    @Test fun upperCaseTagsQuotedAndUnquotedAttributesParseAndCommentedLinksAreIgnored() {
        val links = parseIconLinks(
            """<!-- <link rel="icon" href="/hidden.png"> --><LINK REL='Icon' HREF='/one.png'><link rel=apple-touch-icon href=/two.png?a=1&amp;b=2>""",
            base,
        )
        assertEquals(listOf("https://a.com/one.png", "https://a.com/two.png?a=1&b=2"), links.map { it.url })
    }

    @Test fun protocolRelativeBecomesHttpsRelativeResolvesAgainstThePathAndHttpIsDropped() {
        val links = parseIconLinks(
            """<link rel="icon" href="//cdn.a.com/x.png"><link rel="icon" href="img/y.png"><link rel="icon" href="http://a.com/z.png">""",
            "https://www.a.com/dir/page",
        )
        assertEquals(listOf("https://cdn.a.com/x.png", "https://www.a.com/dir/img/y.png"), links.map { it.url })
    }

    @Test fun at50TagsAreExaminedAnd2048CharacterHrefsAreTheLimit() {
        val tags = (1..51).joinToString("") { """<link rel="icon" href="/$it.png">""" }
        assertEquals(50, parseIconLinks(tags, base).size)
        val okPath = "/" + "a".repeat(2047 - "https://a.com/".length + 1)
        val ok = okPath.let { "https://a.com$it" }
        assertEquals(2048, ok.length)
        assertEquals(1, parseIconLinks("""<link rel="icon" href="$ok">""", base).size)
        assertEquals(0, parseIconLinks("""<link rel="icon" href="${ok}b">""", base).size)
    }

    @Test fun faviconIcoComesLastIsNotDuplicatedAndAtMostFourAreTried() {
        assertEquals(
            listOf("https://a.com/favicon.ico", "https://a.com/x.png", ),
            ordered("""<link rel="icon" sizes="64x64" href="/favicon.ico"><link rel="icon" sizes="32x32" href="/x.png">"""),
        )
        val many = (1..6).joinToString("") { """<link rel="icon" sizes="${it}0x${it}0" href="/$it.png">""" }
        val result = ordered(many)
        assertEquals(4, result.size)
        assertEquals("https://a.com/6.png", result.first())
        assertTrue("https://a.com/favicon.ico" in result)
        assertEquals("https://a.com/favicon.ico", result.last())
    }

    @Test fun aTagThatOnlyStartsWithLinkGivesNoCandidates() = assertEquals(
        emptyList(),
        parseIconLinks("""<linker rel="icon" href="https://x.com/a.png"><link2 rel="icon" href="/b.png"><link_x rel="icon" href="/c.png">""", base),
    )

    @Test fun noLinksStillTriesFaviconIco() = assertEquals(listOf("https://a.com/favicon.ico"), ordered("<html><head></head></html>"))

    private fun parsesFast(html: String) {
        val start = System.nanoTime()
        parseIconLinks(html, base)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 1000, "took $ms ms")
    }

    @Test fun aMegabyteOfUnclosedCommentsParsesInLinearTime() = parsesFast("<!--".repeat(262_144))

    @Test fun aMegabyteOfUnclosedLinkTagsParsesInLinearTime() = parsesFast("<link ".repeat(174_763))

    @Test fun aLinkAfterAnUnclosedCommentIsStillFound() =
        assertEquals(listOf("https://a.com/a.png", "https://a.com/favicon.ico"), ordered("""<!-- x <link rel="icon" href="/a.png">"""))
}
