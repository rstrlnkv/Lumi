package ru.digitalpunk.lumi.icon

import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/** Reads the source resources: a unit test runs in the module folder. */
class LauncherIconResourcesTest {
    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.child(tag: String) = getElementsByTagName(tag).item(0) as Element

    @Test fun theApplicationUsesTheLauncherMipmap() {
        val application = parse("src/main/AndroidManifest.xml").child("application")
        assertEquals("@mipmap/ic_launcher", application.getAttributeNS(ANDROID, "icon"))
    }

    @Test fun theAdaptiveIconHasBackgroundForegroundAndMonochromeThatExist() {
        val icon = parse("src/main/res/mipmap-anydpi/ic_launcher.xml")
        assertEquals("adaptive-icon", icon.tagName)
        for (layer in listOf("background", "foreground", "monochrome")) {
            val drawable = icon.child(layer).getAttributeNS(ANDROID, "drawable")
            assertEquals("@drawable/ic_launcher_$layer", drawable)
        }
        for (layer in listOf("background", "monochrome")) {
            assertTrue(File("src/main/res/drawable/ic_launcher_$layer.xml").isFile, "missing $layer")
        }
        assertTrue(File("src/main/res/drawable-nodpi/ic_launcher_foreground.png").isFile, "missing foreground")
        // Two resources with one name (xml and png) fail the build.
        assertFalse(File("src/main/res/drawable/ic_launcher_foreground.xml").exists())
    }

    @Test fun theIconFolderIsNotVersioned() {
        assertFalse(File("src/main/res/mipmap-anydpi-v26").exists())
    }

    @Test fun theForegroundIsA432PixelPngWithAlpha() {
        val file = File("src/main/res/drawable-nodpi/ic_launcher_foreground.png")
        assertEquals(listOf(0x89, 'P'.code, 'N'.code, 'G'.code), file.readBytes().take(4).map { it.toInt() and 0xFF })
        val image = ImageIO.read(file)
        assertEquals(432 to 432, image.width to image.height)
        assertTrue(image.colorModel.hasAlpha())
        assertEquals(0, image.getRGB(0, 0) ushr 24, "the corner is transparent")
    }

    // The launcher masks the 108 dp layer: only the central 66 dp circle (264 of 432 px) is always visible.
    @Test fun theOpaqueArtOfTheForegroundStaysInsideTheSafeCircle() {
        val image = ImageIO.read(File("src/main/res/drawable-nodpi/ic_launcher_foreground.png"))
        var farthest = 0.0
        var opaque = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image.getRGB(x, y) ushr 24 > 128) {
                opaque++
                farthest = maxOf(farthest, Math.hypot(x + 0.5 - 216.0, y + 0.5 - 216.0))
            }
        }
        assertTrue(opaque > 1000, "the art is there: $opaque opaque pixels")
        assertTrue(farthest <= 132.0, "the art reaches $farthest px from the centre; the safe circle is 132 px")
    }

    @Test fun theMonochromeLayerIsTheFlashAloneFilledBlack() {
        val paths = parse("src/main/res/drawable/ic_launcher_monochrome.xml").getElementsByTagName("path")
        assertEquals(1, paths.length)
        val flash = paths.item(0) as Element
        assertEquals("#FF000000", flash.getAttributeNS(ANDROID, "fillColor"))
        assertFalse(flash.hasAttributeNS(ANDROID, "strokeColor"))
    }

    @Test fun downloadNotificationsUseTheLumiStatusIcon() {
        val notifier = File("src/main/java/ru/digitalpunk/lumi/downloads/DownloadNotifier.kt").readText()
        assertTrue("R.drawable.ic_stat_lumi" in notifier)
        assertFalse("ic_stat_download" in notifier)
        val stat = parse("src/main/res/drawable/ic_stat_lumi.xml")
        assertEquals("24dp", stat.getAttributeNS(ANDROID, "width"))
        val fills = stat.getElementsByTagName("path").let { l -> (0 until l.length).map { (l.item(it) as Element).getAttributeNS(ANDROID, "fillColor") } }
        assertEquals(listOf("@android:color/white"), fills)
    }
}
