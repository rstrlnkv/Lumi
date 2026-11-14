package ru.digitalpunk.lumi.engine

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The site colours in the page report (docs: 012-page-end-spacer-extension): the bottom bar's colour and the status
 * colour (theme-color, else a header stuck to the top). Runs the content script with `node` in the page-end harness.
 * A report prints as "true b#rrggbb s#rrggbb"; the harness prints "bad" for any other key or value.
 */
class PageEndSiteColorTest {
    private val content = File("src/main/assets/lumi-page-end/content.js").absolutePath
    private val harness = File("src/test/resources/page-end-harness.js").absolutePath
    private val node: String? = listOf("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node")
        .firstOrNull { File(it).canExecute() }
        ?: System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, "node") }?.firstOrNull { it.canExecute() }?.path

    private val white = "rgb(255, 255, 255)"
    private val clear = "rgba(0, 0, 0, 0)"

    private fun nav(overrides: String = "") =
        """{"position":"fixed","width":360,"height":56,"bottom":800,"bg":"rgb(11, 87, 208)","bgImage":"none",""" +
            """"visibility":"visible","opacity":"1"$overrides}"""

    private fun header(overrides: String = "") =
        """{"position":"fixed","width":360,"height":56,"top":0,"bg":"rgb(106, 27, 154)","bgImage":"none",""" +
            """"visibility":"visible","opacity":"1"$overrides}"""

    private fun meta(content: String, media: String? = null) =
        """{"name":"theme-color","content":"$content"""" + (media?.let { ""","media":"$it"""" } ?: "") + "}"

    /** What each step printed: with "prop":"sent" (the default) the reports it sent, or the named property. */
    private fun run(vararg steps: String): List<String> {
        val node = assertNotNull(node, "node is required; install Node.js")
        val withProp = steps.map { if ("\"prop\"" in it) it else it.dropLast(1) + ""","prop":"sent"}""" }
        val p = ProcessBuilder(node, harness, content, "[" + withProp.joinToString(",") + "]").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        assertEquals(true, p.waitFor(30, TimeUnit.SECONDS), "node did not finish: $out")
        assertEquals(0, p.exitValue(), out)
        return Regex("\"([^\"]*)\"").findAll(out).map { it.groupValues[1] }.toList()
    }

    /** The one report a page sends on load. */
    private fun onLoad(init: String) = run("""{"init":{"colors":true,$init},"event":"load"}""").single()

    // Bottom colour: the first element on the hit path that paints, walked up to the stuck box until an opaque layer.
    @Test fun anOpaqueBottomBarReportsItsColour() = assertEquals("true b#0b57d0", onLoad(""""nav":${nav()}"""))

    // "rgb(255, 204, 0)" ends in ", 0)" like a transparent "rgba(0, 0, 0, 0)"; it still paints.
    @Test fun aBarWhoseBlueIsZeroPaints() = assertEquals("true b#ffcc00", onLoad(""""nav":${nav(""","bg":"rgb(255, 204, 0)"""")}"""))

    @Test fun aClearBarWithAnOpaqueChildOnTheHitPathTakesTheChildsColour() =
        assertEquals("true b#ff0000", onLoad(""""nav":${nav(""","bg":"$clear","childBg":"rgb(255, 0, 0)"""")}"""))

    // Owner's rule (2026-11-14): a translucent bar is still a bar. Its colour is what the eye sees over a plain page:
    // its layers blended over the page behind it, up to the first opaque layer.
    @Test fun aTranslucentBarIsBlendedOverThePageBehindIt() {
        assertEquals("true b#808080", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")},"bodyBg":"$white""""))
        assertEquals("true b#808080", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")},"htmlBg":"$white""""))
        assertEquals("true b#ff8080", onLoad(""""nav":${nav(""","bg":"rgba(0 0 0 / 0%)","childBg":"rgb(255 0 0 / 50%)"""")},"bodyBg":"$white""""))
        assertEquals("true b#000080", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 255, 0.5)"""")},"bodyBg":"rgb(0, 0, 0)""""))
    }

    @Test fun aClearBarOverAWhitePageIsNoBar() =
        assertEquals("false", onLoad(""""nav":${nav(""","bg":"$clear"""")},"bodyBg":"$white","htmlBg":"$white""""))

    // A page with no background of its own shows the canvas: white unless the page sets a color-scheme.
    @Test fun aTranslucentBarOverAPageWithoutABackgroundIsBlendedOverWhite() =
        assertEquals("true b#808080", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")}"""))

    // With a color-scheme the canvas may be dark, and its colour cannot be read: a bar, colour unknown.
    @Test fun aTranslucentBarOverACanvasOfUnknownColourIsABarWithoutAColour() {
        assertEquals("true", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")},"htmlScheme":"dark""""))
        assertEquals("true", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")},"htmlScheme":"light dark""""))
        assertEquals("true b#808080", onLoad(""""nav":${nav(""","bg":"rgba(0, 0, 0, 0.5)"""")},"htmlScheme":"normal""""))
    }

    @Test fun anOpaqueBarIsNotTintedByAPageColourBehindIt() =
        assertEquals("true b#0b57d0", onLoad(""""nav":${nav()},"bodyBg":"rgb(255, 0, 0)""""))

    @Test fun anImageAGradientOrAnUnreadableColourGivesNoColour() {
        assertEquals("true", onLoad(""""nav":${nav(""","bgImage":"url(a.png)"""")}"""))
        assertEquals("true", onLoad(""""nav":${nav(""","bg":"$clear","bgImage":"linear-gradient(red, blue)"""")}"""))
        assertEquals("true", onLoad(""""nav":${nav(""","bg":"color(display-p3 1 0 0)"""")}"""))
        assertEquals("true", onLoad(""""nav":${nav(""","bg":"oklch(0.5 0.1 200)"""")}"""))
    }

    @Test fun noBottomBarNoBottomColour() = assertEquals("false", onLoad(""""nav":null"""))

    // Status colour: theme-color first, honouring its media query.
    @Test fun aThemeColourIsTheStatusColour() =
        assertEquals("false s#1a73e8", onLoad(""""metas":[${meta("#1a73e8")}]"""))

    @Test fun theMetaForTheCurrentSchemeIsChosen() {
        val metas = """"metas":[${meta("#202124", "(prefers-color-scheme: dark)")},${meta("#1a73e8", "(prefers-color-scheme: light)")}]"""
        assertEquals("false s#1a73e8", onLoad(metas))
        assertEquals("false s#202124", onLoad("""$metas,"dark":true"""))
    }

    @Test fun theFirstValidMetaWinsAndInvalidOrTranslucentOnesAreSkipped() {
        assertEquals("false s#111111", onLoad(""""metas":[${meta("#111111")},${meta("#222222")}]"""))
        assertEquals("false s#222222", onLoad(""""metas":[${meta("")},${meta("#222222")}]"""))
        assertEquals("false s#222222", onLoad(""""metas":[${meta("not a colour")},${meta("#222222")}]"""))
        assertEquals("false s#222222", onLoad(""""metas":[${meta("rgba(1, 2, 3, 0.5)")},${meta("#222222")}]"""))
        assertEquals("false s#010203", onLoad(""""metas":[${meta("#010203")}]"""))
        assertEquals("false s#040506", onLoad(""""metas":[${meta("#040506")}]"""))
    }

    @Test fun aNamedOrShortColourIsNormalised() {
        assertEquals("false s#ff0000", onLoad(""""metas":[${meta("red")}]"""))
        assertEquals("false s#aabbcc", onLoad(""""metas":[${meta("#ABC")}]"""))
    }

    @Test fun aTranslucentThemeColourFallsBackToTheHeader() =
        assertEquals("false s#6a1b9a", onLoad(""""metas":[${meta("rgba(1, 2, 3, 0.5)")}],"header":${header()}"""))

    @Test fun aMetaAddedLaterIsReportedOnTheHeadChange() {
        assertEquals("head:attributeFilter+attributes+childList+subtree=content+media+name",
            run("""{"init":{"colors":true},"event":"DOMContentLoaded","prop":"observedHead"}""").single())
        assertEquals(
            listOf("false", "false s#1a73e8"),
            run(
                """{"init":{"colors":true},"event":"DOMContentLoaded"}""",
                """{"patch":{"metas":[${meta("#1a73e8")}],"now":1000},"headMutation":true}""",
            ),
        )
    }

    @Test fun aSchemeChangeIsReported() {
        val metas = """"metas":[${meta("#202124", "(prefers-color-scheme: dark)")},${meta("#1a73e8")}]"""
        assertEquals(
            listOf("false s#1a73e8", "false s#202124"),
            run("""{"init":{"colors":true,$metas},"event":"load"}""", """{"patch":{"dark":true,"now":1000},"mediaChange":true}"""),
        )
    }

    // Status colour from a header stuck to the top, mirroring the bottom bar's rule.
    @Test fun aFixedOrStickyFullWidthHeaderAtTheTopGivesTheStatusColour() {
        assertEquals("false s#6a1b9a", onLoad(""""header":${header()}"""))
        assertEquals("false s#6a1b9a", onLoad(""""header":${header(""","position":"sticky"""")}"""))
    }

    @Test fun aHeaderThatIsNotStuckToTheTopWideVisibleAndLowIsNotAHeader() {
        for (o in listOf(""","top":40""", ""","width":200""", ""","height":320""", ""","visibility":"hidden"""",
            ""","opacity":"0.3"""", ""","position":"static"""", ""","position":"absolute"""")) {
            assertEquals("false", onLoad(""""header":${header(o)}"""), o)
        }
    }

    @Test fun theThemeColourBeatsTheHeaderWithoutHitTestingTheTop() =
        assertEquals(
            listOf("false s#1a73e8", "0"),
            run(
                """{"init":{"colors":true,"metas":[${meta("#1a73e8")}],"header":${header()}},"event":"load"}""",
                """{"prop":"topHits"}""",
            ),
        )

    @Test fun bothColoursTravelTogether() =
        assertEquals("true b#0b57d0 s#6a1b9a", onLoad(""""nav":${nav()},"header":${header()}"""))

    // Dedupe: only a change is sent; a forced report always is.
    @Test fun anUnchangedReportIsNotResentAChangedColourIsAndPageshowForcesOne() =
        assertEquals(
            listOf("true b#0b57d0", "", "true b#ff0000", "true b#ff0000"),
            run(
                """{"init":{"colors":true,"nav":${nav()}},"event":"load"}""",
                """{"patch":{"now":1000},"event":"scroll"}""",
                """{"patch":{"nav":${nav(""","bg":"rgb(255, 0, 0)"""")},"now":2000},"event":"scroll"}""",
                """{"event":"pageshow"}""",
            ),
        )

    // Cost: one check with a bar, a header and no theme-color stays well under the bound.
    @Test fun aCheckCallsGetComputedStyleABoundedNumberOfTimes() {
        val (_, calls) = run(
            """{"init":{"nav":${nav()},"header":${header()},"bodyBg":"$white"},"event":"load","prop":"styles"}""",
            """{"patch":{"now":1000},"event":"scroll","prop":"styles"}""",
        )
        assertTrue(calls.toInt() in 1..450, "$calls calls")
    }

    // --- Edge inputs added by the tester pass (M8d) ---

    // A colour the parser cannot read in a computed style still counts as painted, but gives no colour.
    @Test fun aChannelAbove255OrAnAlphaAbove1OrAMalformedFunctionGivesNoColour() {
        for (bg in listOf("rgb(256, 0, 0)", "rgb(300, 0, 0)", "rgba(255, 0, 0, 2)", "rgb(1, 2)", "rgb(a, b, c)", "rgb(1, 2, 3, 4, 5)")) {
            assertEquals("true", onLoad(""""nav":${nav(""","bg":"$bg"""")}"""), bg)
        }
        assertEquals("true b#ff0000", onLoad(""""nav":${nav(""","bg":"rgb(255, 0, 0)"""")}"""))
        assertEquals("true b#ff0000", onLoad(""""nav":${nav(""","bg":"rgba(255, 0, 0, 1)"""")}"""))
    }

    // The blend is source over destination: a quarter-opaque black child over a white bar is light, not dark.
    @Test fun aTranslucentChildOverAnOpaqueBarIsBlendedWeightedByItsAlpha() {
        assertEquals("true b#bfbfbf", onLoad(""""nav":${nav(""","bg":"$white","childBg":"rgba(0, 0, 0, 0.25)"""")}"""))
        assertEquals("true b#404040", onLoad(""""nav":${nav(""","bg":"$white","childBg":"rgba(0, 0, 0, 0.75)"""")}"""))
    }

    // Two translucent layers are blended in order: black at 0.25 over white, then red at 0.5 over that.
    @Test fun twoTranslucentLayersAreBlendedOverAnOpaquePage() =
        assertEquals("true b#df6060", onLoad(
            """"nav":${nav(""","bg":"rgba(0, 0, 0, 0.25)","childBg":"rgba(255, 0, 0, 0.5)"""")},"bodyBg":"$white"""",
        ))

    // The length cap is 100 characters: a longer value is not read, a long valid one is.
    @Test fun aThemeColourOf100CharactersIsReadAndOneOf101IsSkipped() {
        assertEquals("false s#1a73e8", onLoad(""""metas":[${meta("rgb(26, 115, 232)")}]"""))
        assertEquals("false s#ff0000", onLoad(""""metas":[${meta("red" + " ".repeat(97))}]"""))
        assertEquals("false s#222222", onLoad(""""metas":[${meta("red" + " ".repeat(98))},${meta("#222222")}]"""))
    }

    @Test fun aTransparentThemeColourIsSkippedForTheNextOne() {
        assertEquals("false s#222222", onLoad(""""metas":[${meta("transparent")},${meta("#222222")}]"""))
        assertEquals("false", onLoad(""""metas":[${meta("transparent")}]"""))
    }

    @Test fun aMetaForTheOtherSchemeOnlyFallsBackToTheHeader() {
        val metas = """"metas":[${meta("#1a73e8", "(prefers-color-scheme: light)")}],"header":${header()}"""
        assertEquals("false s#1a73e8", onLoad(metas))
        assertEquals("false s#6a1b9a", onLoad("""$metas,"dark":true"""))
    }

    @Test fun aHeaderWithAGradientOrNoPaintGivesNoStatusColour() {
        assertEquals("false", onLoad(""""header":${header(""","bg":"$clear","bgImage":"linear-gradient(red, blue)"""")}"""))
        assertEquals("false", onLoad(""""header":${header(""","bgImage":"url(a.png)"""")}"""))
        assertEquals("false", onLoad(""""header":${header(""","bg":"$clear"""")}"""))
    }

    // The header must start within 4 px of the top (and no more than 1 px above it); the hit test sees it from y=1 or y=5.
    @Test fun aHeaderStartingAtTheTopEdgeIsAcceptedWithinTheToleranceAndRejectedBeyondIt() {
        assertEquals("false s#6a1b9a", onLoad(""""header":${header(""","top":-1""")}"""))
        assertEquals("false s#6a1b9a", onLoad(""""header":${header(""","top":4""")}"""))
        assertEquals("false", onLoad(""""header":${header(""","top":-3""")}"""))
        assertEquals("false", onLoad(""""header":${header(""","top":5""")}"""))
    }

    // A header behind the bottom bar's rule: an element at the bottom is not a header and vice versa.
    @Test fun aBottomBarIsNotTakenForAHeaderAndTheOtherWayRound() {
        assertEquals("true b#0b57d0", onLoad(""""nav":${nav()}"""))
        assertEquals("false s#6a1b9a", onLoad(""""header":${header()}"""))
    }

    // A frame other than the top one sends nothing.
    @Test fun aSubframeSendsNothing() =
        assertEquals("", run("""{"init":{"colors":true,"top":false,"nav":${nav()},"metas":[${meta("#1a73e8")}]},"event":"load"}""").single())

    // A colour ending in ", 0)" is not transparent: a page that ends in rgb(255, 204, 0) gives the spacer that colour.
    @Test fun aPageEndingInAColourWhoseBlueIsZeroGivesTheSpacerThatColour() {
        val strip = """{"stripPx":168,"viewWidthPx":1080}"""
        assertEquals(
            listOf("rgb(255, 204, 0)"),
            run("""{"init":{"initialStrip":$strip,"bodyBg":"$white","endBg":"rgb(255, 204, 0)"},"event":"load","prop":"--lumi-page-end-color"}"""),
        )
    }

    // Control for the defect test above: the same page ending in a colour that does not end in ", 0)" is taken.
    @Test fun aPageEndingInAColourWhoseBlueIsOneGivesTheSpacerThatColour() {
        val strip = """{"stripPx":168,"viewWidthPx":1080}"""
        assertEquals(
            listOf("rgb(255, 204, 1)"),
            run("""{"init":{"initialStrip":$strip,"bodyBg":"$white","endBg":"rgb(255, 204, 1)"},"event":"load","prop":"--lumi-page-end-color"}"""),
        )
    }
}
