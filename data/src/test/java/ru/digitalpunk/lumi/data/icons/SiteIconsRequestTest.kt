package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteIconsRequestTest {
    private val dir = File(createTempDirectory("icons").toFile(), "icons")
    private val store = SiteIconStore(dir)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val calls = AtomicInteger()
    private val logged = mutableListOf<String>()

    private fun icons(now: Long = System.currentTimeMillis(), fetch: suspend (IconTarget) -> ByteArray?) =
        SiteIcons(store, { calls.incrementAndGet(); fetch(it) }, scope, Dispatchers.Default, { now }, { m, _ -> synchronized(logged) { logged += m } })

    private suspend fun idle() = job.children.toList().forEach { it.join() }

    @Test fun twoRequestsForOneHostWhileTheFirstRunsFetchOnce() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val icons = icons { gate.await(); byteArrayOf(1) }
        icons.request("https://a.com/x")
        icons.request("http://a.com/y")
        delay(50)
        gate.complete(Unit)
        idle()
        assertEquals(1, calls.get())
    }

    @Test fun aFreshFileFetchesNothingAndAStaleOneFetchesOnce() = runBlocking<Unit> {
        store.write("a.com", byteArrayOf(1))
        val written = store.lastModified("a.com")!!
        icons(now = written + 2_592_000_000L - 1) { byteArrayOf(2) }.request("https://a.com/")
        idle()
        assertEquals(0, calls.get())
        icons(now = written + 2_592_000_000L) { byteArrayOf(2) }.request("https://a.com/")
        idle()
        assertEquals(1, calls.get())
    }

    @Test fun aFailedHostIsSkippedWithoutRetryAndFetchedAgainWithRetry() = runBlocking<Unit> {
        val icons = icons { null }
        icons.request("https://a.com/")
        idle()
        icons.request("https://a.com/", retryFailed = false)
        idle()
        assertEquals(1, calls.get())
        icons.request("https://a.com/", retryFailed = true)
        idle()
        assertEquals(2, calls.get())
    }

    @Test fun fiveHostsAtOnceNeverRunMoreThanThreeFetches() = runBlocking<Unit> {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val icons = icons {
            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            delay(100)
            running.decrementAndGet()
            byteArrayOf(1)
        }
        listOf("a", "b", "c", "d", "e").forEach { icons.request("https://$it.com/") }
        idle()
        assertEquals(5, calls.get())
        assertEquals(3, peak.get())
    }

    @Test fun cancellingTheScopeStopsTheFetchAndWritesNothing() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val icons = icons { started.complete(Unit); delay(10_000); byteArrayOf(1) }
        icons.request("https://a.com/")
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertNull(store.read("a.com"))
        assertEquals(0, dir.list()!!.size)
    }

    @Test fun anExceptionFromTheFetchDoesNotEscapeAndIsLogged() = runBlocking<Unit> {
        val icons = icons { throw IllegalStateException("boom") }
        icons.request("https://a.com/")
        idle()
        assertTrue(job.isActive)
        assertEquals(1, logged.size)
        assertEquals(0, icons.changes.value)
    }

    @Test fun aSuccessfulWriteBumpsChangesOnce() = runBlocking<Unit> {
        val icons = icons { byteArrayOf(7) }
        icons.request("https://a.com/")
        idle()
        assertEquals(1, icons.changes.value)
        assertTrue(store.read("a.com") != null)
    }

    @Test fun aUrlWithoutAnIconHostDoesNothing() = runBlocking<Unit> {
        icons { byteArrayOf(1) }.request("about:blank")
        idle()
        assertEquals(0, calls.get())
    }

    @Test fun aSuccessAfterAFailureClearsTheFailedMarkSoALaterStaleRefreshIsNotSkipped() = runBlocking<Unit> {
        var ok = false
        var clock = System.currentTimeMillis()
        val icons = SiteIcons(store, { calls.incrementAndGet(); if (ok) byteArrayOf(1) else null }, scope, Dispatchers.Default, { clock }, { _, _ -> })
        icons.request("https://a.com/")
        idle()
        ok = true
        icons.request("https://a.com/")
        idle()
        assertEquals(2, calls.get())
        assertTrue(store.read("a.com") != null)
        clock += 2_592_000_000L + 1_000
        icons.request("https://a.com/", retryFailed = false)
        idle()
        assertEquals(3, calls.get())
    }

    @Test fun forgetDeletesTheFileClearsTheFailedMarkAndAnnouncesAChange() = runBlocking<Unit> {
        store.write("a.com", byteArrayOf(1))
        val icons = icons { null }
        icons.request("https://b.com/")
        idle()
        val before = icons.changes.value
        icons.forget(setOf("a.com", "b.com"))
        assertNull(store.read("a.com"))
        assertEquals(before + 1, icons.changes.value)
        icons.request("https://b.com/", retryFailed = false)
        idle()
        assertEquals(2, calls.get())
    }

    @Test fun forgetOfAHostWithNoFileAnnouncesNothing() {
        val icons = icons { null }
        icons.forget(setOf("none.com"))
        assertEquals(0, icons.changes.value)
    }

    @Test fun retainOnlyDeletesTheIconsOfOtherHosts() {
        store.write("a.com", byteArrayOf(1))
        store.write("b.com", byteArrayOf(2))
        val icons = icons { null }
        icons.retainOnly(setOf("b.com"))
        assertNull(store.read("a.com"))
        assertEquals(2, store.read("b.com")?.single()?.toInt())
        assertEquals(1, icons.changes.value)
    }

    @Test fun anIconFetchedWhileItsHostWasForgottenOrNotRetainedIsNotWritten() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val icons = icons { gate.await(); byteArrayOf(1) }
        icons.request("https://a.com/")
        icons.request("https://b.com/")
        icons.request("https://c.com/")
        delay(50)
        icons.forget(setOf("a.com"))
        icons.retainOnly(setOf("c.com"))
        gate.complete(Unit)
        idle()
        assertNull(store.read("a.com"))
        assertNull(store.read("b.com"))
        assertTrue(store.read("c.com") != null)
        icons.request("https://a.com/", retryFailed = false)
        idle()
        assertTrue(store.read("a.com") != null)
    }
}
