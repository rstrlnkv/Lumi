package ru.digitalpunk.lumi.engine

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Inputs the bottom-bar report was not written against: other elements on top of the nav, late mounts, history events,
 * a clock that jumps, no hit at all. Runs the content script with `node` in the page-end harness, like PageEndContentScriptTest.
 */
class PageEndBottomBarEdgeTest {
    private val content = File("src/main/assets/lumi-page-end/content.js").absolutePath
    private val harness = File("src/test/resources/page-end-harness.js").absolutePath
    private val node: String? = listOf("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node")
        .firstOrNull { File(it).canExecute() }
        ?: System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, "node") }?.firstOrNull { it.canExecute() }?.path

    private val white = "rgb(255, 255, 255)"

    private fun nav(overrides: String = "") =
        """{"position":"fixed","width":360,"height":56,"bottom":800,"bg":"$white","bgImage":"none",""" +
            """"visibility":"visible","opacity":"1"$overrides}"""

    /** What each step printed: with "prop":"sent" the reports it sent, or the named property. */
    private fun run(prop: String, vararg steps: String): List<String> {
        val node = assertNotNull(node, "node is required; install Node.js")
        val withProp = steps.map { if ("\"prop\"" in it) it else it.dropLast(1) + ""","prop":"$prop"}""" }
        val p = ProcessBuilder(node, harness, content, "[" + withProp.joinToString(",") + "]").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        assertEquals(true, p.waitFor(30, TimeUnit.SECONDS), "node did not finish: $out")
        assertEquals(0, p.exitValue(), out)
        return Regex("\"([^\"]*)\"").findAll(out).map { it.groupValues[1] }.toList()
    }

    private fun sent(vararg steps: String) = run("sent", *steps)

    private fun onLoad(navJson: String?, init: String = "") = sent("""{"init":{"nav":$navJson$init},"event":"load"}""")

    // A floating button or menu on top of part of the nav hides it at some of the three probed points, not all.
    @Test fun aButtonOnTopOfPartOfTheNavDoesNotHideTheNav() {
        assertEquals(listOf("true"), onLoad(nav(""","cover":[0.4,0.6]""")), "centre covered")
        assertEquals(listOf("true"), onLoad(nav(""","cover":[0.0,0.6]""")), "left and centre covered")
        assertEquals(listOf("true"), onLoad(nav(""","cover":[0.4,1.0]""")), "centre and right covered")
    }

    @Test fun aNavCoveredAtEveryProbedPointByAnotherElementIsNotReported() =
        assertEquals(listOf("false"), onLoad(nav(""","cover":[0.0,1.0]""")))

    @Test fun aPageWhereNothingIsHitReportsFalseWithoutThrowing() =
        assertEquals(listOf("false"), onLoad(nav(), ""","noHit":true"""))

    @Test fun aNavExactlyHalfOpaqueCountsAndOneThatIsNotOpaqueEnoughDoesNot() {
        assertEquals(listOf("true"), onLoad(nav(""","opacity":"0.5"""")))
        assertEquals(listOf("false"), onLoad(nav(""","opacity":"0.49"""")))
    }

    @Test fun aNavWithNoHeightOrNoOpacityValueIsNotReported() {
        assertEquals(listOf("false"), onLoad(nav(""","height":0""")))
        assertEquals(listOf("false"), onLoad(nav(""","opacity":null""")))
    }

    // A footer that sticks to the bottom is a bar at that moment: it is reported, and it clears when the page scrolls it away.
    @Test fun aStickyFooterAtTheBottomIsReportedAndClearsWhenItLeavesTheBottom() =
        assertEquals(
            listOf("true", "false"),
            sent(
                """{"init":{"nav":${nav(""","position":"sticky"""")}},"event":"load"}""",
                """{"patch":{"nav":${nav(""","position":"sticky","bottom":700""")},"now":1000},"event":"scroll"}""",
            ),
        )

    // The 1 s check covers a nav mounted soon after load; the 3 s one a nav mounted by a slower script.
    @Test fun aNavMountedBetweenOneAndThreeSecondsAfterLoadIsFound() =
        assertEquals(
            listOf("false", "", "true"),
            sent(
                """{"init":{"nav":null},"event":"load"}""",
                """{"advance":1000}""",
                """{"patch":{"nav":${nav()}},"advance":2000}""",
            ),
        )

    @Test fun aHistoryPopRechecks() =
        assertEquals(
            listOf("true", "false"),
            sent("""{"init":{"nav":${nav()}},"event":"load"}""", """{"patch":{"nav":null,"now":1000},"event":"popstate"}"""),
        )

    @Test fun aReportIsSentWhenOnlyDomContentLoadedHasFired() =
        assertEquals(listOf("true"), sent("""{"init":{"nav":${nav()}},"event":"DOMContentLoaded"}"""))

    @Test fun theBodyIsWatchedForChildrenAndForStyleAndClassChanges() =
        assertEquals(
            listOf("root:attributeFilter+attributes,body:attributeFilter+attributes+childList"),
            run("observed", """{"init":{"nav":null},"event":"DOMContentLoaded"}"""),
        )

    // Date.now() is the wall clock: a step back (a clock set by hand or by the network) must not park the check for hours.
    @Test fun aClockThatJumpsBackDoesNotStallTheChecks() =
        assertEquals(
            listOf("true", "", "false"),
            sent(
                """{"init":{"nav":${nav()},"now":100000},"event":"load"}""",
                """{"patch":{"nav":null,"now":0},"event":"scroll"}""",
                """{"advance":300}""",
            ),
        )
}
