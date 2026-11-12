package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PageStateStoreInitialTest {
    @Test fun startsFromTheGivenState() {
        assertEquals("https://a.com", PageStateStore(PageState(url = "https://a.com")).state.value.url)
    }
}
