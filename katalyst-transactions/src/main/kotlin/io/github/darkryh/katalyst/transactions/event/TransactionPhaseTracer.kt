package io.github.darkryh.katalyst.transactions.event

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Traces transaction phase execution for debugging and monitoring.
 *
 * Records:
 * - Phase start/end times
 * - Event counts per phase
 * - Handler execution times
 * - Rollback triggers
 * - Duration metrics
 *
 * Useful for:
 * - Performance debugging
 * - Identifying bottlenecks
 * - Monitoring handler execution
 * - Detecting race conditions
 */
data class TransactionPhaseEvent(
    val transactionId: String = UUID.randomUUID().toString(),
    val phase: TransactionPhase,
    val timestamp: Instant = Instant.now(),
    val eventType: EventType,
    val eventCount: Int = 0,
    val durationMs: Long = 0,
    val handlerCount: Int = 0,
    val failedHandlerCount: Int = 0,
    val error: String? = null
) {
    enum class EventType {
        PHASE_STARTED,
        EVENTS_VALIDATED,
        EVENTS_PUBLISHED,
        EVENTS_FAILED,
        EVENTS_QUEUED,
        PHASE_COMPLETED,
        ROLLBACK_TRIGGERED,
        ERROR_OCCURRED
    }
}

/**
 * Records phase transition events for transaction lifecycle.
 *
 * Example usage:
 * ```kotlin
 * val tracer = TransactionPhaseTracer()
 * tracer.recordPhaseStart(TransactionPhase.BEFORE_COMMIT)
 * // ... do work ...
 * tracer.recordPhaseComplete(TransactionPhase.BEFORE_COMMIT, eventCount = 5, durationMs = 50)
 * ```
 */
class TransactionPhaseTracer {
    private val logger = LoggerFactory.getLogger(TransactionPhaseTracer::class.java)
    private val events = mutableListOf<TransactionPhaseEvent>()
    private val phaseTimings = ConcurrentHashMap<String, Long>()

    /**
     * Record transaction phase started.
     */
    fun recordPhaseStart(
        phase: TransactionPhase,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        phaseTimings["$transactionId:${phase.name}:start"] = System.currentTimeMillis()
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.PHASE_STARTED
        )
        events.add(event)
        logger.debug("Transaction phase started: {} (txId: {})", phase, transactionId)
    }

    /**
     * Record events validated.
     */
    fun recordEventsValidated(
        phase: TransactionPhase,
        eventCount: Int,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.EVENTS_VALIDATED,
            eventCount = eventCount
        )
        events.add(event)
        logger.debug("Events validated: {} event(s) in {}", eventCount, phase)
    }

    /**
     * Record events published.
     */
    fun recordEventsPublished(
        phase: TransactionPhase,
        eventCount: Int,
        handlerCount: Int,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.EVENTS_PUBLISHED,
            eventCount = eventCount,
            handlerCount = handlerCount
        )
        events.add(event)
        logger.debug("Events published: {} event(s) with {} handler(s) in {}", eventCount, handlerCount, phase)
    }

    /**
     * Record events failed.
     */
    fun recordEventsFailed(
        phase: TransactionPhase,
        eventCount: Int,
        failedCount: Int,
        error: String? = null,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.EVENTS_FAILED,
            eventCount = eventCount,
            failedHandlerCount = failedCount,
            error = error
        )
        events.add(event)
        logger.warn("Events failed: {} of {} event(s) in {} - {}", failedCount, eventCount, phase, error)
    }

    /**
     * Record events queued for later processing.
     */
    fun recordEventsQueued(
        phase: TransactionPhase,
        eventCount: Int,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.EVENTS_QUEUED,
            eventCount = eventCount
        )
        events.add(event)
        logger.debug("Events queued for later: {} event(s) in {}", eventCount, phase)
    }

    /**
     * Record phase completed.
     */
    fun recordPhaseComplete(
        phase: TransactionPhase,
        eventCount: Int = 0,
        durationMs: Long = 0,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.PHASE_COMPLETED,
            eventCount = eventCount,
            durationMs = durationMs
        )
        events.add(event)
        logger.debug("Phase completed: {} ({} event(s), {}ms)", phase, eventCount, durationMs)
    }

    /**
     * Record rollback triggered.
     */
    fun recordRollback(
        phase: TransactionPhase,
        eventCount: Int,
        reason: String? = null,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.ROLLBACK_TRIGGERED,
            eventCount = eventCount,
            error = reason
        )
        events.add(event)
        logger.warn("Rollback triggered: {} event(s) discarded in {} - {}", eventCount, phase, reason)
    }

    /**
     * Record phase error.
     */
    fun recordError(
        phase: TransactionPhase,
        error: Exception,
        transactionId: String = UUID.randomUUID().toString()
    ) {
        val event = TransactionPhaseEvent(
            transactionId = transactionId,
            phase = phase,
            eventType = TransactionPhaseEvent.EventType.ERROR_OCCURRED,
            error = error.message
        )
        events.add(event)
        logger.error("Phase error: {} - {}", phase, error.message, error)
    }

    /**
     * Get all recorded events.
     */
    fun getEvents(): List<TransactionPhaseEvent> = events.toList()

    /**
     * Get events for specific phase.
     */
    fun getPhaseEvents(phase: TransactionPhase): List<TransactionPhaseEvent> {
        return events.filter { it.phase == phase }
    }

    /**
     * Get summary of transaction execution.
     */
    fun getSummary(): String {
        if (events.isEmpty()) {
            return "No transaction events recorded"
        }

        val firstEvent = events.first()
        val lastEvent = events.last()
        val duration = lastEvent.timestamp.toEpochMilli() - firstEvent.timestamp.toEpochMilli()
        val totalEvents = events.sumOf { it.eventCount }
        val failedCount = events.filter {
            it.eventType == TransactionPhaseEvent.EventType.EVENTS_FAILED
        }.sumOf { it.failedHandlerCount }

        return buildString {
            append("Transaction Summary: ")
            append("phases=${events.size}, ")
            append("totalEvents=$totalEvents, ")
            append("failed=$failedCount, ")
            append("duration=${duration}ms")
        }
    }

    /**
     * Clear recorded events.
     */
    fun clear() {
        events.clear()
        phaseTimings.clear()
    }

    /**
     * Get detailed metrics.
     */
    fun getMetrics(): TransactionMetrics {
        val publishedCount = events
            .filter { it.eventType == TransactionPhaseEvent.EventType.EVENTS_PUBLISHED }
            .sumOf { it.eventCount }

        val failedCount = events
            .filter { it.eventType == TransactionPhaseEvent.EventType.EVENTS_FAILED }
            .sumOf { it.failedHandlerCount }

        val totalDuration = events
            .filter { it.eventType == TransactionPhaseEvent.EventType.PHASE_COMPLETED }
            .sumOf { it.durationMs }

        return TransactionMetrics(
            totalPhases = events.size,
            totalEvents = publishedCount,
            failedEvents = failedCount,
            totalDurationMs = totalDuration,
            averageDurationMs = if (events.isNotEmpty()) totalDuration / events.size else 0
        )
    }

    /**
     * Transaction execution metrics.
     */
    data class TransactionMetrics(
        val totalPhases: Int,
        val totalEvents: Int,
        val failedEvents: Int,
        val totalDurationMs: Long,
        val averageDurationMs: Long
    )
}

/**
 * Scope function for tracing a transaction phase.
 *
 * Example:
 * ```kotlin
 * val tracer = TransactionPhaseTracer()
 * tracer.tracePhase(TransactionPhase.BEFORE_COMMIT, eventCount = 5) {
 *     // Do work...
 *     logger.info("Publishing events")
 * }
 * ```
 */
inline fun <T> TransactionPhaseTracer.tracePhase(
    phase: TransactionPhase,
    eventCount: Int = 0,
    transactionId: String = UUID.randomUUID().toString(),
    block: (tracer: TransactionPhaseTracer) -> T
): T {
    val startTime = System.currentTimeMillis()
    recordPhaseStart(phase, transactionId)

    return try {
        val result = block(this)
        val duration = System.currentTimeMillis() - startTime
        recordPhaseComplete(phase, eventCount, duration, transactionId)
        result
    } catch (e: Exception) {
        recordError(phase, e, transactionId)
        throw e
    }
}
