package ru.digitalpunk.lumi.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class AppLinkInboxTest {
    private fun link(n: Int) = IncomingIntent("android.intent.action.VIEW", "https://a.test/$n", null)

    @Test fun linksArriveInOrder() = runBlocking<Unit> {
        val inbox = AppLinkInbox()
        (1..3).forEach { inbox.offer(link(it)) }
        assertEquals((1..3).map(::link), inbox.items.take(3).toList())
    }

    @Test fun theNinthPendingLinkDropsTheOldest() = runBlocking<Unit> {
        val inbox = AppLinkInbox()
        (1..9).forEach { inbox.offer(link(it)) }
        assertEquals((2..9).map(::link), inbox.items.take(8).toList())
    }
}
