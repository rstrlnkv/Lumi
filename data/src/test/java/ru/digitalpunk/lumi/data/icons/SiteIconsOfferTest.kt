package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Collections
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SiteIconsOfferTest {
    private val store = SiteIconStore(File(createTempDirectory("offer").toFile(), "icons"))
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val offers = Collections.synchronizedList(mutableListOf<PageIconOffer?>())

    private fun icons(fetch: suspend () -> FetchedIcon? = { FetchedIcon(fakePng(), IconMeta(192, false, null)) }) =
        SiteIcons(store, { _, o -> offers += o; fetch() }, scope, Dispatchers.Default, System::currentTimeMillis, { _, _ -> }).apply { wantedHosts = setOf("a.com") }

    private suspend fun idle() = job.children.toList().forEach { it.join() }

    private fun offer(page: String = "https://a.com", vararg urls: String) =
        PageIconOffer(page, urls.map { IconCandidate(it, IconCandidate.Kind.Touch, 180) }, null, null)

    @Test fun aPageOfAHostWithoutABookmarkStartsNothing() = runBlocking<Unit> {
        icons().offerPageLinks(offer("https://b.com", "https://b.com/t.png"))
        idle()
        assertEquals(0, offers.size)
    }

    @Test fun theSameLinksAreTriedOncePerProcess() = runBlocking<Unit> {
        val icons = icons { null }
        repeat(2) { icons.offerPageLinks(offer(urls = arrayOf("https://a.com/t.png"))); idle() }
        icons.offerPageLinks(offer(urls = arrayOf("https://a.com/u.png"))); idle()
        assertEquals(2, offers.size)
    }

    @Test fun anOfferWhileTheHostIsBeingFetchedIsDropped() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val icons = icons { gate.await(); null }
        icons.request("https://a.com/")
        delay(50)
        icons.offerPageLinks(offer(urls = arrayOf("https://a.com/t.png")))
        gate.complete(Unit); idle()
        assertEquals(listOf<PageIconOffer?>(null), offers)
    }

    @Test fun aFreshSharpStoredIconIsLeftAloneButASmallOneIsUpgraded() = runBlocking<Unit> {
        store.write("a.com", IconMeta.insert(fakePng(), IconMeta(192, false, null))!!)
        icons().offerPageLinks(offer(urls = arrayOf("https://a.com/t.png"))); idle()
        assertEquals(0, offers.size)
        store.write("a.com", IconMeta.insert(fakePng(), IconMeta(32, false, null))!!)
        icons().offerPageLinks(offer(urls = arrayOf("https://a.com/t.png"))); idle()
        assertEquals(1, offers.size)
        assertEquals(192, store.meta("a.com")?.srcPx)
    }

    @Test fun linksThatAreNotSafeHttpsAreDroppedBeforeTheFetch() = runBlocking<Unit> {
        icons().offerPageLinks(offer(urls = arrayOf("http://a.com/t.png", "https://u@a.com/t.png", "https://a.com:8443/t.png", "https://10.0.0.1/t.png", "https://a.com/ok.png")))
        idle()
        assertEquals(listOf("https://a.com/ok.png"), offers.single()!!.candidates.map { it.url })
    }
}
