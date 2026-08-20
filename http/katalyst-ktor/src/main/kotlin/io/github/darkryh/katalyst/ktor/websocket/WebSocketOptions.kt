package io.github.darkryh.katalyst.ktor.websocket

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class WebSocketOptions(
    val pingPeriod: Duration? = null,
    val timeout: Duration = 15.seconds,
    val maxFrameSize: Long = Long.MAX_VALUE,
    val masking: Boolean = false,
)

class WebSocketOptionsBuilder {
    var pingPeriod: Duration? = null
    var timeout: Duration = 15.seconds
    var maxFrameSize: Long = Long.MAX_VALUE
    var masking: Boolean = false

    fun build(): WebSocketOptions {
        require(!timeout.isNegative()) { "WebSocket timeout must not be negative" }
        require(maxFrameSize > 0) { "WebSocket maxFrameSize must be greater than zero" }
        pingPeriod?.let {
            require(!it.isNegative()) { "WebSocket pingPeriod must not be negative" }
        }
        return WebSocketOptions(
            pingPeriod = pingPeriod,
            timeout = timeout,
            maxFrameSize = maxFrameSize,
            masking = masking,
        )
    }
}
