package com.ead.katalyst.ktor.engine

/**
 * Marker interface for Katalyst Ktor engine implementations.
 *
 * Each engine module provides a singleton object implementing this interface.
 * The engine object is used during DI bootstrap to determine which engine
 * module to load and register.
 *
 * Example implementations:
 * - NettyEngine (from katalyst-ktor-engine-netty)
 * - JettyEngine (from katalyst-ktor-engine-jetty)
 * - CioEngine (from katalyst-ktor-engine-cio)
 */
interface KatalystKtorEngine {
    /**
     * Returns the engine type identifier (netty, jetty, cio).
     * Used for logging and validation.
     */
    val engineType: String
}
