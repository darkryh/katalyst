package com.ead.katalyst.example.api

import kotlinx.serialization.Serializable

/**
 * Health Check and Error Response DTOs
 *
 * These data classes are used for serializing health check and error responses.
 * Using data classes instead of maps ensures type safety and proper serialization
 * by Kotlin's kotlinx.serialization framework.
 *
 * **Why use data classes instead of mapOf()?**
 * - Kotlin serialization (kotlinx.serialization) requires explicit types
 * - Maps with mixed types (String + Long) cause serialization errors
 * - Data classes provide type safety and better IDE support
 * - JSON output is identical, but serialization is guaranteed to work
 */

/**
 * Simple health status response
 *
 * Used by GET /health endpoint
 */
@Serializable
data class HealthStatusResponse(
    val status: String,
    val timestamp: Long
)

/**
 * Detailed health status with service information
 *
 * Used by GET /health/detailed endpoint
 */
@Serializable
data class DetailedHealthResponse(
    val status: String,
    val services: Map<String, String>,
    val timestamp: Long
)

/**
 * Error Response DTO
 *
 * Used for all exception handlers and error responses.
 * Ensures type-safe serialization by using explicit fields instead of maps.
 *
 * **Usage in exception handlers:**
 * Instead of:
 * ```kotlin
 * mapOf(
 *     "error" to "VALIDATION_ERROR",
 *     "message" to exception.message,
 *     "timestamp" to System.currentTimeMillis()
 * )
 * ```
 *
 * Use:
 * ```kotlin
 * ErrorResponse(
 *     error = "VALIDATION_ERROR",
 *     message = exception.message,
 *     timestamp = System.currentTimeMillis()
 * )
 * ```
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String?,
    val timestamp: Long
)
