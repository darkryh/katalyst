package com.ead.katalyst.messaging

enum class RoutingType {
    DIRECT,
    TOPIC,
    FANOUT
}

data class RoutingConfig(
    val routingType: RoutingType = RoutingType.DIRECT,
    val routingKey: String? = null
)
