package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.TabsState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureWindowTest {
    private val a = TabInfo(TabId("a"), "", "https://a", false, false)
    private val p = TabInfo(TabId("p"), "", "https://p", false, false, PRIVATE_GROUP)
    private val normal = TabsState(listOf(a, p), a.id, privateCount = 1)

    @Test fun secureWhenAPrivateTabIsInFront() {
        assertTrue(needsSecureWindow(normal.copy(selectedId = p.id, currentGroup = PRIVATE_GROUP), gridOpen = false))
    }

    @Test fun secureWhenTheGridShowsThePrivateChip() {
        assertTrue(needsSecureWindow(normal.copy(currentGroup = PRIVATE_GROUP), gridOpen = true))
    }

    @Test fun secureWhileResumeIsPending() {
        assertTrue(needsSecureWindow(normal.copy(tabs = listOf(a), privateLocked = true, privateResume = true), gridOpen = false))
    }

    @Test fun notSecureForANormalTab() {
        assertFalse(needsSecureWindow(normal, gridOpen = false))
    }

    @Test fun notSecureForTheGridOfANormalGroup() {
        assertFalse(needsSecureWindow(normal, gridOpen = true))
    }

    @Test fun notSecureWhenPrivateChipIsCurrentButGridClosedAndNormalInFront() {
        assertFalse(needsSecureWindow(normal.copy(currentGroup = PRIVATE_GROUP), gridOpen = false))
    }
}
