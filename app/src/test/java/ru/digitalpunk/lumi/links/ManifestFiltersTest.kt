package ru.digitalpunk.lumi.links

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/** Reads the source manifest: a unit test runs in the module folder. */
class ManifestFiltersTest {
    private val activity: Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        .getElementsByTagName("activity").let { list -> (0 until list.length).map { list.item(it) as Element } }
        .single { it.attr("name") == ".MainActivity" }

    private fun Element.attr(name: String) = getAttributeNS(ANDROID, name)
    private fun Element.children(tag: String) = getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.names(tag: String) = children(tag).map { it.attr("name") }.toSet()
    private val filters = activity.children("intent-filter")
    private fun filterFor(action: String) = filters.single { action in it.names("action") }

    @Test fun mainActivityIsSingleTask() {
        assertEquals("singleTask", activity.attr("launchMode"))
    }

    @Test fun viewFilterTakesBrowsableHttpAndHttpsLinks() {
        val view = filterFor("android.intent.action.VIEW")
        assertEquals(setOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"), view.names("category"))
        assertEquals(setOf("http", "https"), view.children("data").map { it.attr("scheme") }.toSet())
    }

    @Test fun webSearchFilterHasTheDefaultCategory() {
        assertEquals(setOf("android.intent.category.DEFAULT"), filterFor("android.intent.action.WEB_SEARCH").names("category"))
    }

    @Test fun noFilterTakesShares() {
        assertTrue(filters.none { "android.intent.action.SEND" in it.names("action") })
    }

    @Test fun lumiDeclaresTheAndroid17LocalNetworkPermission() {
        val manifest = activity.ownerDocument.documentElement
        assertTrue("android.permission.ACCESS_LOCAL_NETWORK" in manifest.names("uses-permission"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
