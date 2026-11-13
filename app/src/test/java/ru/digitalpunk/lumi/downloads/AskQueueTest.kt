package ru.digitalpunk.lumi.downloads

import ru.digitalpunk.lumi.engine.DownloadOffer
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Closing : ByteArrayInputStream(ByteArray(1)) {
    var closed = false
    override fun close() { closed = true }
}

class AskQueueTest {
    private fun offer(private: Boolean = false, s: Closing = Closing()) = DownloadOffer("https://x.org/a", null, null, -1, "x.org", private, s)

    @Test fun offersQueueInArrivalOrderAndTheHeadIsShown() {
        val q = AskQueue()
        val a = offer(); val b = offer()
        q.add(a); q.add(b)
        assertEquals(a, q.offers.value.first())
        q.remove(a)
        assertEquals(listOf(b), q.offers.value)
    }

    @Test fun aFullQueueDiscardsTheNewOfferSoAPageCannotPileUpStreams() {
        val q = AskQueue()
        repeat(MAX_ASKS) { q.add(offer()) }
        val s = Closing()
        q.add(offer(s = s))
        assertTrue(s.closed)
        assertEquals(MAX_ASKS, q.offers.value.size)
    }

    @Test fun droppingPrivateOffersDiscardsTheirStreamsAndKeepsNormalOnes() {
        val q = AskQueue()
        val s = Closing()
        val normal = offer()
        q.add(offer(private = true, s = s)); q.add(normal)
        q.dropPrivate()
        assertTrue(s.closed)
        assertEquals(listOf(normal), q.offers.value)
    }

    @Test fun cancelDiscardsTheStream() {
        val q = AskQueue()
        val s = Closing()
        val o = offer(s = s)
        q.add(o)
        q.cancel(o)
        assertTrue(s.closed)
        assertNull(q.offers.value.firstOrNull())
    }
}

class PermissionPlanTest {
    @Test fun storageIsAskedOnlyOnApi26To28() {
        assertTrue(permissionPlan(26, storageGranted = false, notificationsGranted = false, notificationsAsked = false).askStorage)
        assertTrue(permissionPlan(28, false, false, false).askStorage)
        assertFalse(permissionPlan(28, true, false, false).askStorage)
        assertFalse(permissionPlan(29, false, false, false).askStorage)
    }

    @Test fun notificationsAreAskedOnceFromApi33() {
        assertFalse(permissionPlan(32, true, false, false).askNotifications)
        assertTrue(permissionPlan(33, true, false, false).askNotifications)
        assertTrue(permissionPlan(37, true, false, false).askNotifications)
        assertFalse(permissionPlan(33, true, false, true).askNotifications)
        assertFalse(permissionPlan(33, true, true, false).askNotifications)
    }

    @Test fun neverBothOnOneDevice() {
        for (sdk in listOf(26, 28, 29, 32, 33, 37)) {
            val plan = permissionPlan(sdk, false, false, false)
            assertFalse(plan.askStorage && plan.askNotifications, "$sdk")
        }
    }

    @Test fun theHintShowsOncePerProcessWhileNotificationsAreOff() {
        assertTrue(showNoNotificationHint(enabled = false, shownThisProcess = false))
        assertFalse(showNoNotificationHint(enabled = false, shownThisProcess = true))
        assertFalse(showNoNotificationHint(enabled = true, shownThisProcess = false))
    }
}
