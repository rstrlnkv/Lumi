package ru.digitalpunk.lumi.ui.settings

import ru.digitalpunk.lumi.data.ClearItem
import ru.digitalpunk.lumi.data.DEFAULT_CLEAR_CHOICE
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteBrowsingDataTest {
    private val source = File("src/main/java/ru/digitalpunk/lumi/ui/settings/DeleteBrowsingData.kt").readText()

    @Test fun `delete is disabled with no box`() {
        assertFalse(canDelete(emptySet()))
        assertTrue(canDelete(setOf(ClearItem.Tabs)))
        assertTrue(canDelete(DEFAULT_CLEAR_CHOICE))
    }

    @Test fun `the order is tabs, engine, history`() {
        val body = source.substringAfter("suspend fun deleteBrowsingData(")
        val tabs = body.indexOf("closeNormalTabs()")
        val engine = body.indexOf("clearBrowsingData(")
        val history = body.indexOf("clearHistory(ClearRange.AllTime")
        assertTrue(tabs in 0 until engine && engine < history, "tabs $tabs, engine $engine, history $history")
    }

    @Test fun `the sheet never offers permissions`() {
        val privacy = File("src/main/java/ru/digitalpunk/lumi/ui/settings/PrivacySettings.kt").readText()
        listOf(source, privacy).forEach {
            assertFalse("PERMISSIONS" in it)
            assertFalse("SITE_SETTINGS" in it)
        }
    }
}
