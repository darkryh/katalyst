package com.ead.katalyst.testing.core

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * Default test engine for KatalystTestEnvironment.
 *
 * Provides a basic mock engine implementation for use in test environments
 * without requiring actual Ktor engine modules on the classpath.
 */
object TestKatalystEngine : KatalystKtorEngine {
    override val engineType: String = "test"

    override fun toString(): String = "TestKatalystEngine(type='test')"
}
