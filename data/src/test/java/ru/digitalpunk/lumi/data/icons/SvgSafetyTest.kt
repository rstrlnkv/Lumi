package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SvgSafetyTest {
    private val ok = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><defs><linearGradient id="g"/></defs><path fill="url(#g)" d="M0 0h16v16z"/><use href="#p"/></svg>"""

    @Test fun aPlainIconWithLocalReferencesIsSafe() = assertTrue(svgSafe(ok))

    @Test fun scriptsExternalReferencesEntitiesAndCostlyEffectsAreRefused() {
        val bad = listOf(
            "<script>x</script>", "<SCRIPT/>", "<foreignObject/>", "<iframe/>", "<image href=\"#a\"/>", "<feImage/>",
            "<!ENTITY a \"b\">", "<?xml-stylesheet href=\"#a\"?>", "<style>@import 'x.css';</style>",
            "<filter id=\"f\"/>", "<pattern id=\"p\"/>", "<mask id=\"m\"/>", "<path stroke-dasharray=\"1\"/>",
            "<use href=\"https://x.com/a.svg#p\"/>", "<use xlink:href='//x.com/a#p'/>", "<use href = \"a.svg\"/>",
            "<path fill=\"url(https://x.com/#g)\"/>", "<path style=\"fill:url( 'x.svg#g')\"/>", "<rect onload=\"x()\"/>", "<rect\tONclick =\"x\"/>",
        )
        for (b in bad) assertFalse(svgSafe(ok.replace("<path fill", "$b<path fill")), b)
        assertFalse(svgSafe("<!DOCTYPE svg [ <!ELEMENT a ANY> ]>$ok"))
    }

    @Test fun tooManyTagsTooDeepOrUnclosedIsRefused() {
        assertFalse(svgSafe("<svg>" + "<g>".repeat(65) + "</g>".repeat(65) + "</svg>"))
        assertTrue(svgSafe("<svg>" + "<g>".repeat(60) + "</g>".repeat(60) + "</svg>"))
        assertFalse(svgSafe("<svg>" + "<rect/>".repeat(4001) + "</svg>"))
        assertFalse(svgSafe("<svg><rect"))
    }

    @Test fun moreThanFourUseTagsAreRefusedSoUseDoublingCannotExplode() {
        val use = "<use href=\"#p\"/>"
        assertTrue(svgSafe(ok.replace(use, use.repeat(4))))
        assertFalse(svgSafe(ok.replace(use, use.repeat(4) + "<USE\nhref=\"#p\"/>")))
        // Each group draws the previous one twice: 30 levels are 2^30 draws.
        val doubling = (1..30).joinToString("") { "<g id=\"g$it\"><use href=\"#g${it - 1}\"/><use href=\"#g${it - 1}\"/></g>" }
        assertFalse(svgSafe("<svg viewBox=\"0 0 1 1\"><defs><path id=\"g0\" d=\"M0 0h1v1z\"/>$doubling</defs><use href=\"#g30\"/></svg>"))
    }

    @Test fun aMegabyteOfUnclosedTagsIsRefusedQuickly() {
        val t0 = System.nanoTime()
        assertFalse(svgSafe("<a".repeat(500_000)))
        assertFalse(svgSafe("<svg " + "href".repeat(250_000)))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000)
    }

    @Test fun svgIsSniffedAfterABomWhitespaceAnXmlDeclarationOrAComment() {
        assertTrue(looksLikeSvg(ok.toByteArray()))
        assertTrue(looksLikeSvg(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "\n  <?xml version=\"1.0\"?><!-- x --><svg/>".toByteArray()))
        assertTrue(looksLikeSvg("<!-- Generator --><SVG/>".toByteArray()))
        assertFalse(looksLikeSvg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertFalse(looksLikeSvg("<html><svg/></html>".toByteArray()))
    }

    @Test fun aRenderWithLessThanTwoPercentInkIsBlank() {
        assertFalse(hasInk(IntArray(1000) { if (it < 19) -1 else 0 }))
        assertTrue(hasInk(IntArray(1000) { if (it < 20) -1 else 0 }))
    }
}
