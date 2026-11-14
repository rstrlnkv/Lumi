package ru.digitalpunk.lumi.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The shield's "Delete data of this site" row is Compose wiring, so these read the source. */
class ProtectionSheetWiringTest {
    private val screen = File("src/main/java/ru/digitalpunk/lumi/ui/BrowserScreen.kt").readText()
    private val sheet = File("src/main/java/ru/digitalpunk/lumi/ui/ProtectionSheet.kt").readText()
    private val call = screen.substringAfter("ProtectionSheet(").substringBefore("onDismiss = { protectionTab = null }")

    @Test fun `private tabs get no delete row`() {
        assertTrue("onDeleteData = if (selectedPrivate) null else" in call)
        assertTrue("onDeleteData?.let" in sheet)
    }

    @Test fun `the row reloads after the delete`() {
        val action = call.substringAfter("onDeleteData = ")
        val clear = action.indexOf("clearSiteData(")
        val reload = action.indexOf("reload()")
        assertTrue(clear >= 0 && reload > clear, "clear $clear, reload $reload")
    }
}
