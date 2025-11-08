package com.ead.katalyst.messaging

interface Producer {
    suspend fun send(destination: Destination, message: Message, routing: RoutingConfig? = null)
}

interface Consumer {
    suspend fun consume(destination: Destination, routing: RoutingConfig? = null, handler: suspend (Message) -> Unit)
}
