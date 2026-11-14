package ru.digitalpunk.lumi.engine

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The content script's rule, run by `node` against a fake page (`src/test/resources/page-end-harness.js`).
 * Fails when `node` is not installed: Node.js is required to run `./gradlew test`.
 */
class PageEndContentScriptTest {
    private val content = File("src/main/assets/lumi-page-end/content.js").absolutePath
    private val harness = File("src/test/resources/page-end-harness.js").absolutePath
    private val node: String? = listOf("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node")
        .firstOrNull { File(it).canExecute() }
        ?: System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, "node") }?.firstOrNull { it.canExecute() }?.path

    private fun strip(px: Int, width: Int) = """{"stripPx":$px,"viewWidthPx":$width}"""

    /** The harness's exit code and output. */
    private fun exec(vararg steps: String): Pair<Int, String> {
        val node = assertNotNull(node, "node is required to test the page-end content script; install Node.js")
        val p = ProcessBuilder(node, harness, content, "[" + steps.joinToString(",") + "]").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        assertEquals(true, p.waitFor(30, TimeUnit.SECONDS), "node did not finish: $out")
        return p.exitValue() to out
    }

    /** The value of `--lumi-page-end` on <html> after each step; "" means the property is not set. */
    private fun run(vararg steps: String): List<String> {
        val (code, out) = exec(*steps)
        assertEquals(0, code, out)
        return Regex("\"([^\"]*)\"").findAll(out).map { it.groupValues[1] }.toList()
    }

    private fun page(init: String, strip: String = strip(168, 1080)) = """{"init":{$init},"strip":$strip}"""

    @Test fun theHarnessFailsWhenFramesKeepSchedulingFrames() {
        val (code, out) = exec(page(""), """{"loopFrames":true}""")
        assertEquals(1, code, out)
        assertTrue("frames never settle" in out, out)
    }

    @Test fun theStripIsScaledFromPhysicalPxToCssPxByTheLayoutWidth() {
        assertEquals(listOf("56px"), run(page(""""clientWidth":360""")))
        assertEquals(listOf("112px"), run(page(""""clientWidth":720""")))
    }

    @Test fun aDesktopSiteLaidOutWiderGetsALargerBlockRoundedUp() {
        // 168 * 980 / 1080 = 152.44
        assertEquals(listOf("153px"), run(page(""""clientWidth":980""")))
        // 100 * 333 / 1000 = 33.3
        assertEquals(listOf("34px"), run(page(""""clientWidth":333""", strip(100, 1000))))
    }

    @Test fun aPageThatFitsOneScreenGetsNoBlock() {
        assertEquals(listOf(""), run(page(""""base":500,"innerHeight":800""")))
        assertEquals(listOf(""), run(page(""""base":801,"innerHeight":800""")))
    }

    @Test fun aPageTwoPxTallerThanTheScreenGetsTheBlock() =
        assertEquals(listOf("56px"), run(page(""""base":802,"innerHeight":800""")))

    @Test fun aPageThatLocksScrollingOnHtmlOrBodyIsLeftAlone() {
        for (v in listOf("hidden", "clip")) {
            assertEquals(listOf(""), run(page(""""overflowRoot":"$v"""")), "html $v")
            assertEquals(listOf(""), run(page(""""overflowBody":"$v"""")), "body $v")
        }
    }

    @Test fun otherOverflowValuesAndNearMissesStillGetTheBlock() {
        for (v in listOf("auto", "scroll", "visible", "overlay", "hidden auto", "xhidden")) {
            assertEquals(listOf("56px"), run(page(""""overflowRoot":"$v","overflowBody":"$v"""")), v)
        }
    }

    @Test fun anIframeNeverGetsTheBlock() = assertEquals(listOf(""), run(page(""""top":false""")))

    @Test fun withoutAStripNothingIsSet() = assertEquals(listOf(""), run("""{"init":{}}"""))

    @Test fun anInvalidStripSetsNothing() {
        assertEquals(listOf(""), run(page("", strip(0, 1080))))
        assertEquals(listOf(""), run(page("", strip(168, 0))))
        assertEquals(listOf(""), run(page("", strip(-168, 1080))))
        assertEquals(listOf(""), run(page(""""clientWidth":0""")))
    }

    @Test fun aNewStripValueReplacesTheOldOneAndNullClearsIt() =
        assertEquals(
            listOf("56px", "112px", ""),
            run(page(""), """{"strip":${strip(336, 1080)}}""", """{"strip":null}"""),
        )

    @Test fun aLayoutWidthChangeUpdatesTheBlock() =
        assertEquals(
            listOf("56px", "112px"),
            run(page(""""clientWidth":360"""), """{"patch":{"clientWidth":720},"event":"resize"}"""),
        )

    @Test fun thePageGrowingAndShrinkingAddsAndRemovesTheBlock() =
        assertEquals(
            listOf("", "56px", ""),
            run(
                page(""""base":500"""),
                """{"patch":{"base":2000},"event":"scroll"}""",
                """{"patch":{"base":600},"event":"resize"}""",
            ),
        )

    @Test fun theBlockItselfNeverMakesAPageLookScrollableOrFlipsTheRule() =
        // 820 of content on an 800 screen: with the 56px block the scroll height is 876, still the same page.
        assertEquals(
            listOf("56px", "56px", "56px"),
            run(page(""""base":820"""), """{"event":"resize"}""", """{"event":"scroll"}"""),
        )

    @Test fun aPageThatShrinksToOneScreenLosesItsBlockEvenThoughTheBlockStillCountsInTheScrollHeight() =
        // 800 of content fits an 800 screen; the old 56px block makes the scroll height 856, which must not count.
        assertEquals(
            listOf("56px", ""),
            run(page(""""base":2000"""), """{"patch":{"base":800},"event":"resize"}"""),
        )

    @Test fun aStripOfZeroOrAZeroWidthNeverProducesANonFiniteBlock() {
        assertEquals(listOf(""), run(page("", strip(0, 0))))
        assertEquals(listOf(""), run(page("", strip(168, 0))))
    }

    @Test fun aDocumentWithoutABodyYetDoesNotThrow() =
        assertEquals(listOf("56px"), run(page(""""hasBody":false""")))

    @Test fun aPageThatFitsInPortraitButScrollsInLandscapeGetsTheBlockAfterRotation() =
        // Portrait 800 high with 700 of content: one screen. Landscape 360 high: it scrolls and its end hides under the strip.
        assertEquals(
            listOf("", "56px"),
            run(page(""""base":700,"innerHeight":800"""), """{"patch":{"innerHeight":360},"event":"resize"}"""),
        )

    @Test fun aLateFirstStorageReadDoesNotUndoANewerStripChange() =
        // The read was started with the old 168 px strip; the app already sent 336 px before it settled.
        assertEquals(
            listOf("112px", "112px"),
            run(
                """{"init":{"initialStrip":${strip(168, 1080)},"delayGet":true},"strip":${strip(336, 1080)}}""",
                """{"resolveGet":true}""",
            ),
        )

    private val white = "rgb(255, 255, 255)"
    private val gray = "rgb(245, 245, 247)"
    private fun color(init: String) =
        run(page(init).dropLast(1) + ""","prop":"--lumi-page-end-color"}""")

    @Test fun theBlockTakesTheColourOfTheBoxThePageEndsIn() =
        assertEquals(listOf(gray), color(""""bodyBg":"$white","endBg":"$gray""""))

    @Test fun aBoxThatStopsShortOfThePageEndLeavesTheBodyColour() =
        assertEquals(listOf(white), color(""""bodyBg":"$white","endBg":"$gray","endReaches":false"""))

    @Test fun anEmptyBoxAfterTheFooterDoesNotHideTheFooterColour() =
        assertEquals(listOf(gray), color(""""bodyBg":"$white","endBg":"$gray","emptyLast":true"""))

    @Test fun aPageWithoutAnyBackgroundLeavesTheBlockUncoloured() =
        assertEquals(listOf(""), color(""))

    // Wikipedia: `html, body { height: 100% }` and a 100%-high wrapper, so a box in the flow lands at one screen.
    @Test fun theBlockIsPlacedWhereThePageContentEnds() =
        assertEquals(
            listOf("2000px", "3000px", ""),
            run(
                page("").dropLast(1) + ""","prop":"--lumi-page-end-top"}""",
                """{"patch":{"base":3000},"event":"scroll","prop":"--lumi-page-end-top"}""",
                """{"patch":{"base":500},"event":"scroll","prop":"--lumi-page-end-top"}""",
            ),
        )

    @Test fun contentThatGrowsLessThanTheBlockStillMovesTheBlock() =
        assertEquals(
            listOf("2000px", "2030px"),
            run(
                page("").dropLast(1) + ""","prop":"--lumi-page-end-top"}""",
                """{"patch":{"base":2030},"event":"scroll","prop":"--lumi-page-end-top"}""",
            ),
        )

    // The bottom-bar report (docs: 012-page-end-spacer-extension): one boolean, sent only when it changes or is forced.
    // The default nav: fixed, full width (360), 56 px high, its bottom at the viewport bottom (800), white.
    private fun nav(overrides: String = "") =
        """{"position":"fixed","width":360,"height":56,"bottom":800,"bg":"$white","bgImage":"none",""" +
            """"visibility":"visible","opacity":"1"$overrides}"""

    /** The reports each step sent; every step prints them. */
    private fun sent(vararg steps: String) = run(*steps.map { it.dropLast(1) + ""","prop":"sent"}""" }.toTypedArray())

    private fun onLoad(navJson: String?, init: String = "") =
        sent("""{"init":{"nav":$navJson$init},"event":"load"}""")

    @Test fun aFixedFullWidthNavAtTheBottomIsReported() = assertEquals(listOf("true"), onLoad(nav()))

    @Test fun aPageWithoutOneReportsFalseOnLoad() = assertEquals(listOf("false"), onLoad(null))

    @Test fun aNavThatPaintsNothingIsNotReportedUnlessAChildPaints() {
        val clear = "rgba(0, 0, 0, 0)"
        assertEquals(listOf("false"), onLoad(nav(""","bg":"$clear"""")))
        assertEquals(listOf("true"), onLoad(nav(""","bg":"$clear","childBg":"$white"""")))
        assertEquals(listOf("false"), onLoad(nav(""","bg":"$clear","childBg":"$clear"""")))
        assertEquals(listOf("true"), onLoad(nav(""","bg":"$clear","bgImage":"linear-gradient(red, blue)"""")))
    }

    @Test fun onlyFixedOrStickyCounts() {
        assertEquals(listOf("false"), onLoad(nav(""","position":"static"""")))
        assertEquals(listOf("false"), onLoad(nav(""","position":"absolute"""")))
        assertEquals(listOf("true"), onLoad(nav(""","position":"sticky"""")))
    }

    @Test fun aStickyBoxNotAtTheBottomEdgeIsNotStuck() =
        assertEquals(listOf("false"), onLoad(nav(""","position":"sticky","bottom":780,"height":200""")))

    @Test fun theNavSpansAtLeastFourFifthsOfTheWidth() {
        assertEquals(listOf("true"), onLoad(nav(""","width":288""")))
        assertEquals(listOf("false"), onLoad(nav(""","width":284""")))
        assertEquals(listOf("false"), onLoad(nav(""","width":56""")), "a floating button")
    }

    @Test fun theNavEndsWithinFourPxOfTheViewportBottom() {
        assertEquals(listOf("true"), onLoad(nav(""","bottom":796""")))
        assertEquals(listOf("false"), onLoad(nav(""","bottom":795""")))
        assertEquals(listOf("true"), onLoad(nav(""","bottom":801""")))
        assertEquals(listOf("false"), onLoad(nav(""","bottom":802""")))
    }

    @Test fun aThinLineOrAFullScreenLayerIsNotANav() {
        assertEquals(listOf("false"), onLoad(nav(""","height":15""")))
        assertEquals(listOf("true"), onLoad(nav(""","height":16""")))
        assertEquals(listOf("true"), onLoad(nav(""","height":280""")))
        assertEquals(listOf("false"), onLoad(nav(""","height":281""")))
        assertEquals(listOf("false"), onLoad(nav(""","height":800""")))
    }

    @Test fun aHiddenOrFadedNavIsNotReported() {
        assertEquals(listOf("false"), onLoad(nav(""","visibility":"hidden"""")))
        assertEquals(listOf("false"), onLoad(nav(""","opacity":"0"""")))
        assertEquals(listOf("false"), onLoad(nav(""","opacity":"0.4"""")))
    }

    @Test fun ourOwnPageEndBlockIsNotANav() =
        assertEquals(listOf("false"), sent(page(""""nav":null""").dropLast(1) + ""","event":"load"}"""))

    @Test fun aChangeIsSentOnceAndARecheckWithoutAChangeSendsNothing() =
        assertEquals(
            listOf("true", "false", ""),
            sent(
                """{"init":{"nav":${nav()}},"event":"load"}""",
                """{"patch":{"nav":null,"now":1000},"event":"scroll"}""",
                """{"patch":{"now":2000},"event":"scroll"}""",
            ),
        )

    @Test fun pageshowAndBecomingVisibleResendTheValue() =
        assertEquals(
            listOf("true", "true", "", "true"),
            sent(
                """{"init":{"nav":${nav()}},"event":"load"}""",
                """{"event":"pageshow"}""",
                """{"patch":{"hidden":true},"event":"visibilitychange"}""",
                """{"patch":{"hidden":false},"event":"visibilitychange"}""",
            ),
        )

    @Test fun scrollChecksAreThrottledWithATrailingCheck() =
        assertEquals(
            listOf("true", "false", "", "true"),
            sent(
                """{"init":{"nav":${nav()}},"event":"load"}""",
                """{"patch":{"nav":null,"now":1000},"event":"scroll"}""",
                """{"patch":{"nav":${nav()},"now":1100},"event":"scroll"}""",
                """{"advance":150}""",
            ),
        )

    @Test fun aNavMountedAfterLoadIsFoundByTheTimedRecheck() =
        assertEquals(
            listOf("false", "true"),
            sent("""{"init":{"nav":null},"event":"load"}""", """{"patch":{"nav":${nav()}},"advance":1000}"""),
        )

    @Test fun anSpaRouteChangeRechecks() =
        assertEquals(
            listOf("true", "false"),
            sent("""{"init":{"nav":${nav()}},"event":"load"}""", """{"patch":{"nav":null,"now":1000},"event":"hashchange"}"""),
        )

    // The load's own frame check is already the next check; once the app listens, the next scroll sends it again too.
    @Test fun aRejectedReportIsSentAgainOnTheNextCheck() =
        assertEquals(
            listOf("true,true", "true"),
            sent("""{"init":{"nav":${nav()},"rejectSend":true},"event":"load"}""", """{"patch":{"rejectSend":false,"now":1000},"event":"scroll"}"""),
        )

    @Test fun anIframeReportsNothing() = assertEquals(listOf(""), onLoad(nav(), ""","top":false"""))

    @Test fun aPageWithoutABodyReportsFalseWithoutThrowing() =
        assertEquals(listOf("false"), onLoad(null, ""","hasBody":false"""))
}

