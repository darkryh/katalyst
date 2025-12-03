package io.github.darkryh.katalyst.messaging

enum class DestinationType {
    QUEUE,
    TOPIC,
    STREAM
}

data class Destination(
    val name: String,
    val type: DestinationType
)
