package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** One currently-open WebSocket session (populated once the handler wrapper is instrumented). */
@Serializable
data class WebSocketSession(
    val id: String,
    val path: String,
    val remote: String? = null,
    val principal: String? = null,
    val openedAtEpochMs: Long,
    val framesIn: Long = 0,
    val framesOut: Long = 0,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
)

/**
 * WebSockets: the resolved flag vs the real plugin-installed ground truth, effective-vs-configured
 * options (surfacing the ping-disabled and unbounded-frame footguns), and live sessions/frames.
 */
@Serializable
data class WebSocketSnapshot(
    val enabledFlag: Boolean = false,
    val pluginInstalled: Boolean = false,
    val pingPeriodMs: Long? = null,
    val timeoutMs: Long? = null,
    val maxFrameSizeBytes: Long? = null,
    val masking: Boolean = false,
    /** True when pingPeriod is null/0 => no keepalive => half-open connections leak. */
    val keepaliveDisabled: Boolean = false,
    /** True when maxFrameSize is effectively unbounded (Long.MAX_VALUE) => OOM risk. */
    val frameSizeUnbounded: Boolean = false,
    val routePaths: List<String> = emptyList(),
    val activeSessions: Int = 0,
    val sessionsPerRoute: Map<String, Int> = emptyMap(),
    val opened: Long = 0,
    val closed: Long = 0,
    val handlerErrors: Long = 0,
    val closeCodeCounts: Map<String, Long> = emptyMap(),
    val sessions: List<WebSocketSession> = emptyList(),
)
