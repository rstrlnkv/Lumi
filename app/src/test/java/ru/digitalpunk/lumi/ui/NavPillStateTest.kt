package ru.digitalpunk.lumi.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NavPillStateTest {
    @Test fun whileEditingTheAddressNeitherBackNorForwardShows() {
        assertEquals(NavPillState.Hidden, navPillState(editing = true, canGoForward = false))
        assertEquals(NavPillState.Hidden, navPillState(editing = true, canGoForward = true))
    }

    @Test fun outsideEditingBackShowsAloneUntilTheTabCanGoForward() {
        assertEquals(NavPillState.Back, navPillState(editing = false, canGoForward = false))
        assertEquals(NavPillState.BackAndForward, navPillState(editing = false, canGoForward = true))
    }
}
