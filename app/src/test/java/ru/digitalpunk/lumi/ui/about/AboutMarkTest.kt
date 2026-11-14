package ru.digitalpunk.lumi.ui.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The name + DEV badge line of About Lumi (Figma I2/I3, v2): the name alone is centred, the badge hangs after it. */
class AboutMarkTest {
    private val screen = File("src/main/java/ru/digitalpunk/lumi/ui/about/AboutScreen.kt").readText()

    @Test fun theNameAloneIsCentredAndTheBadgeHangsTheGapAfterIt() {
        val m = markPlacement(name = 62, badge = 37, gap = 8)
        assertEquals(m.width / 2.0, m.nameX + 62 / 2.0)
        assertEquals(m.nameX + 62 + 8, m.badgeX)
        assertEquals(m.badgeX + 37, m.width, "the line ends at the badge; the same space mirrors it before the name")
    }

    @Test fun withoutABadgeTheLineIsTheNameAlone() {
        assertEquals(MarkPlacement(width = 62, nameX = 0, badgeX = 62), markPlacement(name = 62, badge = 0, gap = 0))
    }

    @Test fun theBadgeIsAFullSecondaryPillTopAligned3dpBelowTheNameLineBox() {
        assertTrue("MaterialTheme.colorScheme.onSecondaryContainer" in screen)
        assertTrue(".background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)" in screen)
        assertFalse("tertiaryContainer" in screen || "TertiaryContainer" in screen)
        assertTrue("badgeTop = 3.dp" in screen)
    }

    @Test fun theRowsUseTheFigmaSymbols() {
        for (icon in listOf("ic_tag", "ic_new_releases", "ic_description")) {
            assertTrue("R.drawable.$icon" in screen, icon)
            assertTrue(File("src/main/res/drawable/$icon.xml").exists(), icon)
        }
        assertFalse("Icons.Filled.Info" in screen || "Icons.Filled.Star" in screen || "Icons.AutoMirrored.Filled.List" in screen)
    }
}
