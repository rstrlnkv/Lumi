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

    @Test fun theBandIsAnOpaqueSurfaceOfTheBarSchemeThatTakesTouchesByTheRule() {
        assertTrue(".background(MaterialTheme.colorScheme.surface)" in band, "the bar's own scheme, so the private bar gets the private surface")
        assertTrue("bandTakesTouches(collapsed)" in band)
        assertTrue("pointerInput(Unit) {}" in band)
        assertFalse("alpha" in band || "LocalAppColorScheme" in band, "no fade and no app-wide scheme")
    }

    @Test fun classicKeepsItsOwnBarBackground() =
        assertFalse("hasBottomBar" in classic || "matchParentSize" in classic)
}
