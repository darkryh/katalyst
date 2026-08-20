package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** HikariCP gauge: the core saturation/leak view. */
@Serializable
data class PoolSnapshot(
    val active: Int = 0,
    val idle: Int = 0,
    val pending: Int = 0,
    val total: Int = 0,
    val maxPoolSize: Int = 0,
    val minIdle: Int = 0,
    val closed: Boolean = false,
    /** Should be 1; > 1 means an orphaned duplicate pool. */
    val liveDataSourceCount: Int = 1,
    val connectionTimeouts: Long = 0,
    val leakAlarms: Long = 0,
) {
    val utilization: Double get() = if (maxPoolSize > 0) active.toDouble() / maxPoolSize else 0.0
    val headroom: Int get() = (maxPoolSize - active).coerceAtLeast(0)
}

/** A status→count distribution for the operation_log / workflow_state audit tables. */
@Serializable
data class AuditDistribution(
    val table: String,
    val counts: Map<String, Long> = emptyMap(),
)

/**
 * Persistence: ground truth for "stuck on the database" — pool saturation/leak, orphaned duplicate
 * pool, repo op tallies, and the PENDING/FAILED audit backlog. Pool gauge is already-free.
 */
@Serializable
data class PersistenceSnapshot(
    val pool: PoolSnapshot? = null,
    val databaseConfigured: Boolean = true,
    val saveCount: Long = 0,
    val insertBranch: Long = 0,
    val updateBranch: Long = 0,
    val updateToInsertFallback: Long = 0,
    val findByIdHits: Long = 0,
    val findByIdMisses: Long = 0,
    val deleteCount: Long = 0,
    val mappingValidationFailures: Long = 0,
    val sqlFailures: Long = 0,
    val audit: List<AuditDistribution> = emptyList(),
)
