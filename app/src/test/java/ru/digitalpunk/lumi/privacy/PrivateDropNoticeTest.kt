package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.data.privatetabs.PrivateLoad
import ru.digitalpunk.lumi.data.privatetabs.PrivateStoredTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateDropNoticeTest {
    @Test fun storedTabsThatCouldNotBeOpenedAreTold() = assertTrue(tellsDropped(PrivateLoad.Dropped))

    @Test fun anEmptyStartTellsNothing() = assertFalse(tellsDropped(PrivateLoad.Empty))

    @Test fun restoredTabsTellNothingEvenWhenTheListIsEmpty() {
        assertFalse(tellsDropped(PrivateLoad.Loaded(listOf(PrivateStoredTab("p", "https://p", "P", null)))))
        assertFalse(tellsDropped(PrivateLoad.Loaded(emptyList())))
    }

    @Test fun aTransientRetryIsNotADrop() = assertFalse(tellsDropped(PrivateLoad.Retry))

    @Test fun wipeAfterTheScreenLockWentAwayIsADropOnlyWhenTabsWereThere() {
        assertEquals(PrivateLoad.Dropped, afterWipe(fileWiped = true, openPrivate = 0))
        assertEquals(PrivateLoad.Dropped, afterWipe(fileWiped = false, openPrivate = 2))
        assertEquals(PrivateLoad.Empty, afterWipe(fileWiped = false, openPrivate = 0))
    }
}
