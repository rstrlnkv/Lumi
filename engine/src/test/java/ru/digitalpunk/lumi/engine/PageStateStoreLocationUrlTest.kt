package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PageStateStoreLocationUrlTest {
    private val store = PageStateStore()

    @Test fun locationChangedWithinTheSameSiteUpdatesTheUrl() {
        store.pageStarted("https://a.com/")
        store.locationChanged("https://a.com/next#frag")
        assertEquals("https://a.com/next#frag", store.state.value.url)
    }
}
