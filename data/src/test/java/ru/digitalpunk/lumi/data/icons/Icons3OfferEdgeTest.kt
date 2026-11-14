package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Collections
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Icons3OfferEdgeTest {
    private val store = SiteIconStore(File(createTempDirectory("offer-edge").toFile(), "icons"))
    private val job = SupervisorJob()
    private val offers = Collections.synchronizedList(mutableListOf<PageIconOffer?>())
    private val icons = SiteIcons(
        store, { _, o -> offers += o; null }, CoroutineScope(job + Dispatchers.Default), Dispatchers.Default, System::currentTimeMillis, { _, _ -> },
    ).apply { wantedHosts = setOf("a.com") }

    private suspend fun idle() = job.children.toList().forEach { it.join() }
    private val touch = listOf(IconCandidate("https://a.com/t.png", IconCandidate.Kind.Touch, 180))

    @Test fun aFreshGoodIconOf120PixelsIsStillOfferedLinksThatMayBeSharp() = runBlocking<Unit> {
        store.write("a.com", IconMeta.insert(fakePng(), IconMeta(120, false, null))!!)
        icons.offerPageLinks(PageIconOffer("https://a.com", touch, null, null)); idle()
        assertEquals(1, offers.size)
    }

    @Test fun aManifestUrlThatIsNotSafeHttpsIsDroppedBeforeTheFetchAndASafeOneKept() = runBlocking<Unit> {
        for (m in listOf("http://a.com/m.json", "https://u@a.com/m.json", "https://a.com:8443/m.json", "https://10.0.0.1/m.json")) {
            icons.offerPageLinks(PageIconOffer("https://a.com", touch, m, null)); idle()
            assertNull(offers.last()!!.manifestUrl, m)
        }
        icons.offerPageLinks(PageIconOffer("https://a.com", touch, "https://a.com/m.json", null)); idle()
        assertEquals("https://a.com/m.json", offers.last()!!.manifestUrl)
    }
}
