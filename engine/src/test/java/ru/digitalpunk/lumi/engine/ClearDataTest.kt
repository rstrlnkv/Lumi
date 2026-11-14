package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.StorageController.ClearFlags
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearDataTest {
    @Test fun `cookies are cookies, storage and sign-ins`() {
        assertEquals(ClearFlags.COOKIES or ClearFlags.DOM_STORAGES or ClearFlags.AUTH_SESSIONS, clearFlags(cookies = true, cache = false))
        assertEquals(49L, clearFlags(cookies = true, cache = false))
    }

    @Test fun `cache is every cache`() {
        assertEquals(ClearFlags.ALL_CACHES, clearFlags(cookies = false, cache = true))
    }

    @Test fun `both together`() {
        assertEquals(55L, clearFlags(cookies = true, cache = true))
    }

    @Test fun `never permissions or site settings`() {
        listOf(true to true, true to false, false to true, false to false).forEach { (cookies, cache) ->
            assertEquals(0L, clearFlags(cookies, cache) and ClearFlags.SITE_SETTINGS)
        }
    }
}
