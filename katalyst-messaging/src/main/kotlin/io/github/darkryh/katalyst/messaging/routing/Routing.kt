package io.github.darkryh.katalyst.messaging.routing

enum class RoutingType {
    DIRECT,
    TOPIC,
    FANOUT
}

data class RoutingConfig(
    val routingType: RoutingType = RoutingType.DIRECT,
    val routingKey: String? = null
)
