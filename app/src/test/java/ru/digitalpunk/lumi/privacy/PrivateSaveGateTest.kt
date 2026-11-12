package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.data.privatetabs.PrivateStoredTab
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateSaveGateTest {
    private val tabs = listOf(PrivateStoredTab("p", "https://p", "P", null))

    @Test fun nothingIsWrittenBeforeTheStoredTabsAreRestored() {
        assertFalse(shouldWritePrivate(restored = false, LockAvailability.Available, tabs, lastSaved = null))
        assertTrue(shouldWritePrivate(restored = true, LockAvailability.Available, tabs, lastSaved = null))
    }

    @Test fun nothingIsWrittenWhenNoScreenLock() {
        assertFalse(shouldWritePrivate(restored = true, LockAvailability.NoScreenLock, tabs, lastSaved = null))
        assertFalse(shouldWritePrivate(restored = true, LockAvailability.Unavailable, tabs, lastSaved = null))
    }

    @Test fun identicalSnapshotIsNotWrittenTwice() {
        assertFalse(shouldWritePrivate(restored = true, LockAvailability.Available, tabs, lastSaved = tabs.toList()))
        assertTrue(shouldWritePrivate(restored = true, LockAvailability.Available, emptyList(), lastSaved = tabs))
    }
}
