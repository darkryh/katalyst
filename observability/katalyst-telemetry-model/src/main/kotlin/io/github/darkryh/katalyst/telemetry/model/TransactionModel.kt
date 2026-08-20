package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** A registered transaction adapter in execution order. */
@Serializable
data class AdapterEntry(
    val name: String,
    val priority: Int,
    val critical: Boolean,
)

/** A currently-open transaction (shallow: identity + state + age). */
@Serializable
data class InFlightTransaction(
    val transactionId: String,
    val workflowId: String? = null,
    val scopeState: String,
    val depth: Int = 0,
    val ageMs: Long = 0,
    val pendingEvents: Int = 0,
    val deferredSideEffects: Int = 0,
)

/**
 * Transactions: the dark boundary where atomicity, retry, propagation and post-commit side-effects
 * converge. Adapters + config are already-free; outcome tallies and per-phase streams fill in once
 * the dormant MetricsCollector is wired and the discarded PhaseExecutionResults are captured.
 */
@Serializable
data class TransactionSnapshot(
    val adapters: List<AdapterEntry> = emptyList(),
    val metricsCollectorAttached: Boolean = false,
    val isolationConfigured: String? = null,
    val isolationEffective: String? = null,
    val timeoutMs: Long? = null,
    val maxRetries: Int? = null,
    val committed: Long = 0,
    val rolledBack: Long = 0,
    val timedOut: Long = 0,
    val failed: Long = 0,
    val p50Ms: Double = 0.0,
    val p95Ms: Double = 0.0,
    val p99Ms: Double = 0.0,
    val inFlight: List<InFlightTransaction> = emptyList(),
)
