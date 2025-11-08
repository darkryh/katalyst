package com.ead.katalyst.messaging

enum class DestinationType {
    QUEUE,
    TOPIC,
    STREAM
}

data class Destination(
    val name: String,
    val type: DestinationType
)
