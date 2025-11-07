package com.ead.katalyst.events

import com.ead.katalyst.events.EventMessagingPublisher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Builder-style configuration used by katalystApplication to wire the events module.
 */
class EventConfiguration {
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default
    internal var messagingPublisher: EventMessagingPublisher? = null

    fun applicationBus(dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        this.dispatcher = dispatcher
    }

    fun messaging(publisher: EventMessagingPublisher) {
        this.messagingPublisher = publisher
    }
}

data class EventModuleOptions(
    val dispatcher: CoroutineDispatcher,
    val messagingPublisher: EventMessagingPublisher?
)

fun EventConfiguration.toModuleOptions(): EventModuleOptions =
    EventModuleOptions(
        dispatcher = dispatcher,
        messagingPublisher = messagingPublisher
    )
