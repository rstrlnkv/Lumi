package ru.digitalpunk.lumi.links

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Links from other apps, held until the browser screen has restored the tabs; past [CAPACITY] the oldest is dropped. */
class AppLinkInbox {
    private val channel = Channel<IncomingIntent>(CAPACITY, BufferOverflow.DROP_OLDEST)

    /** Each link goes to one collector, once. */
    val items: Flow<IncomingIntent> = channel.receiveAsFlow()

    fun offer(incoming: IncomingIntent) {
        channel.trySend(incoming)
    }

    private companion object {
        const val CAPACITY = 8
    }
}
