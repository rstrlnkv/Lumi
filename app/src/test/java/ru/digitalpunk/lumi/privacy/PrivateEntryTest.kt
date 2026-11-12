package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.engine.TabsState
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateEntryTest {
    private val unlocked = TabsState(privateCount = 2)
    private val locked = TabsState(privateCount = 2, privateLocked = true)

    @Test fun unlockedAndAvailableAllowsAtOnce() {
        assertEquals(Entry.Allow, decideEntry(LockAvailability.Available, unlocked))
    }

    @Test fun lockedWithTabsNeedsUnlock() {
        assertEquals(Entry.NeedsUnlock, decideEntry(LockAvailability.Available, locked))
    }

    @Test fun lockedWithNoPrivateTabsAllows() {
        assertEquals(Entry.Allow, decideEntry(LockAvailability.Available, TabsState()))
    }

    @Test fun noScreenLockRefuses() {
        assertEquals(Entry.Refuse, decideEntry(LockAvailability.NoScreenLock, locked))
        assertEquals(Entry.Refuse, decideEntry(LockAvailability.Unavailable, TabsState()))
    }

    @Test fun noScreenLockRefusesEvenWhenUnlocked() {
        assertEquals(Entry.Refuse, decideEntry(LockAvailability.NoScreenLock, unlocked))
    }
}
