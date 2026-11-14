package ru.digitalpunk.lumi.engine

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A bottom bar whose background is painted by its ::before or ::after (netflix.com's floating "Get started" bar:
 * a transparent fixed div whose ::after holds a top-to-bottom gradient ending in opaque black). Runs the content
 * script with `node` in the page-end harness; a report prints as "true b#rrggbb" or "true" or "false".
 */
class PageEndPseudoPaintTest {
    private val content = File("src/main/assets/lumi-page-end/content.js").absolutePath
    private val harness = File("src/test/resources/page-end-harness.js").absolutePath
    private val node: String? = listOf("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node")
        .firstOrNull { File(it).canExecute() }
        ?: System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, "node") }?.firstOrNull { it.canExecute() }?.path

    private val netflixGradient =
        "linear-gradient(rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.016) 8.333%, rgba(0, 0, 0, 0.984) 91.67%, rgb(0, 0, 0) 100%)"

    /** A transparent fixed bar (375 wide, 88 high, at the bottom) with the given pseudo-element styles. */
    private fun bar(pseudo: String) =
        """{"position":"fixed","width":375,"height":88,"bottom":800,"bg":"rgba(0, 0, 0, 0)","bgImage":"none",""" +
            """"visibility":"visible","opacity":"1","pseudo":{$pseudo}}"""

    private fun pseudo(name: String, content: String = "\"\\\"\\\"\"", bg: String = "rgba(0, 0, 0, 0)", image: String = "none") =
        """"$name":{"content":$content,"bg":"$bg","bgImage":"$image"}"""

    private fun report(nav: String): String {
        val node = assertNotNull(node, "node is required; install Node.js")
        val step = """{"init":{"colors":true,"clientWidth":375,"innerHeight":800,"nav":$nav},"event":"load","prop":"sent"}"""
        val p = ProcessBuilder(node, harness, content, "[$step]").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        assertEquals(true, p.waitFor(30, TimeUnit.SECONDS), "node did not finish: $out")
        assertEquals(0, p.exitValue(), out)
        return Regex("\"([^\"]*)\"").find(out)!!.groupValues[1]
    }

    @Test fun aTransparentBarWhoseAfterPaintsATopToBottomGradientEndingInBlackReportsABarColouredBlack() =
        assertEquals("true b#000000", report(bar(pseudo("::after", image = netflixGradient))))

    @Test fun aTransparentBarWhoseBeforePaintsAnOpaqueColourReportsThatColour() =
        assertEquals("true b#e50914", report(bar(pseudo("::before", bg = "rgb(229, 9, 20)"))))

    @Test fun aPseudoWithContentNoneNeverCountsEvenWithABackground() =
        assertEquals("false", report(bar(pseudo("::after", content = "\"none\"", bg = "rgb(0, 0, 0)", image = netflixGradient))))

    @Test fun aPseudoGradientInAnotherDirectionCountsAsABarWithNoColour() =
        assertEquals("true", report(bar(pseudo("::after", image = "linear-gradient(to right, rgba(0, 0, 0, 0), rgb(0, 0, 0))"))))

    @Test fun aPseudoGradientToBottomAndOneAt180DegreesGiveTheLastStopsColour() {
        assertEquals("true b#102030", report(bar(pseudo("::after", image = "linear-gradient(to bottom, rgba(0, 0, 0, 0), rgb(16, 32, 48))"))))
        assertEquals("true b#102030", report(bar(pseudo("::after", image = "linear-gradient(180deg, rgba(0, 0, 0, 0), rgb(16, 32, 48))"))))
    }

    @Test fun aGradientPseudoWhoseLastStopIsNotOpaqueCountsAsABarWithNoColour() =
        assertEquals("true", report(bar(pseudo("::after", image = "linear-gradient(rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.5))"))))

    // ::after is drawn over ::before, so when both are opaque the bar shows ::after's colour.
    @Test fun whenBothPseudosPaintOpaqueTheAfterColourWinsOverTheBefore() =
        assertEquals("true b#0000ff", report(bar(
            pseudo("::before", bg = "rgb(255, 0, 0)") + "," + pseudo("::after", bg = "rgb(0, 0, 255)"))))

    // A pseudo is drawn over its element's own background, not under it.
    @Test fun anOpaquePseudoIsReportedOverTheBarsOwnOpaqueBackground() =
        assertEquals("true b#0000ff", report(
            bar(pseudo("::after", bg = "rgb(0, 0, 255)")).replace("\"bg\":\"rgba(0, 0, 0, 0)\"", "\"bg\":\"rgb(255, 0, 0)\""))
        )

    @Test fun aTransparentBarWithNoPseudoPaintIsStillNotReported() =
        assertEquals("false", report(bar("")))
}
