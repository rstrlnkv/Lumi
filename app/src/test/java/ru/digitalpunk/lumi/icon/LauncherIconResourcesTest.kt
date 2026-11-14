package ru.digitalpunk.lumi.icon

import java.io.File
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
            assertTrue(File("src/main/res/drawable/ic_launcher_$layer.xml").isFile, "missing $layer")
        }
    }

    @Test fun theIconFolderIsNotVersioned() {
        assertFalse(File("src/main/res/mipmap-anydpi-v26").exists())
    }

    @Test fun theFlashLayersKeepTheirFigmaOpacity() {
        val alphas = parse("src/main/res/drawable/ic_launcher_foreground.xml").getElementsByTagName("path")
            .let { l -> (0 until l.length).map { (l.item(it) as Element).getAttributeNS(ANDROID, "fillAlpha") } }
        assertTrue("0.36" in alphas && "0.12" in alphas, "fillAlpha values: $alphas")
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
