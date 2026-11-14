package ru.digitalpunk.lumi.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The band is Compose wiring with no pure seam beyond bandTakesTouches, so these read the source: the band is drawn
 * only for a page with a bar stuck to the screen bottom, under the controls, opaque, and only in the Compact bar.
 */
class CompactBandWiringTest {
    private val compact = File("src/main/java/ru/digitalpunk/lumi/ui/CompactBar.kt").readText()
    private val classic = File("src/main/java/ru/digitalpunk/lumi/ui/ClassicBar.kt").readText()

    /** The text of the bar box before its first row of controls. */
    private val band: String get() = compact.substringAfter("Box(modifier.fillMaxWidth()) {").substringBefore("Row(")

    @Test fun theBandIsDrawnOnlyWhenThePageHasABottomBar() {
        assertTrue(band.trimStart().startsWith("if (state.hasBottomBar) {"), band)
        assertEquals(1, Regex("hasBottomBar").findAll(compact).count())
    }

    @Test fun theBandIsTheFirstChildSoTheControlsAndTheStripDrawOverIt() {
        assertTrue("matchParentSize()" in band, "the band fills the bar box: the whole bar expanded, the strip collapsed")
        assertTrue("CollapsedStrip(" !in band && "NavPill(" !in band)
    }

    @Test fun theBandIsTheSiteColourElseTheBarSchemeSurfaceAndTakesTouchesByTheRule() {
        assertTrue(".background(band)" in band)
        assertTrue(
            "val band by animateColorAsState(bandColor(state.bottomBarColor, MaterialTheme.colorScheme.surface), tween(150)" in compact,
            "the bar's own scheme, so the private bar falls back to the private surface; a change animates for 150 ms",
        )
        assertTrue("bandTakesTouches(collapsed)" in band)
        assertTrue("pointerInput(Unit) {}" in band)
        assertFalse("alpha" in band || "LocalAppColorScheme" in band, "no fade and no app-wide scheme")
    }

    @Test fun aBandThatAppearsStartsInItsReportedColourNotTheSurface() = assertTrue(
        "animateColorAsState(bandColor(" in band,
        "the colour state is created with the band: one kept while no band shows holds surface (white in the light theme) " +
            "and fades the new band in from it (netflix.com: a white first frame under its black block)",
    )

    @Test fun classicKeepsItsOwnBarBackground() =
        assertFalse("hasBottomBar" in classic || "matchParentSize" in classic || "bottomBarColor" in classic)
}
