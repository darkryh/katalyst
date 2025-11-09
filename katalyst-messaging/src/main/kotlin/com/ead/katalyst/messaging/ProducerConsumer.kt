package com.ead.katalyst.messaging

import com.ead.katalyst.messaging.routing.RoutingConfig

interface Producer {
    suspend fun send(destination: Destination, message: Message, routing: RoutingConfig? = null)
}

interface Consumer {
    suspend fun consume(destination: Destination, routing: RoutingConfig? = null, handler: suspend (Message) -> Unit)
}
