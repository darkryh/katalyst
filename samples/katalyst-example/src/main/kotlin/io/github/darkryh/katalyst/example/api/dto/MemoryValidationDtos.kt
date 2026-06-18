package io.github.darkryh.katalyst.example.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MemoryValidationTelemetry(
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
    val threadCount: Int,
    val poolActive: Int,
    val poolIdle: Int,
    val poolPending: Int,
    val poolTotal: Int,
    val transactionAdapters: Int,
)
