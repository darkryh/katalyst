package io.github.darkryh.katalyst.messaging

import io.github.darkryh.katalyst.messaging.routing.RoutingConfig

interface Producer {
    suspend fun send(destination: Destination, message: Message, routing: RoutingConfig? = null)
}

interface Consumer {
    suspend fun consume(destination: Destination, routing: RoutingConfig? = null, handler: suspend (Message) -> Unit)
}
