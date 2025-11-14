package com.ead.katalyst.di.config.test

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * Test fixture for KatalystKtorEngine.
 *
 * Allows tests to create engine objects without needing to import
 * from specific engine modules (netty, jetty, cio).
 *
 * This maintains proper architectural separation - katalyst-di module
 * doesn't depend on specific engine implementations.
 */
class MockEngine(override val engineType: String) : KatalystKtorEngine {
    override fun toString(): String = "MockEngine(engineType='$engineType')"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MockEngine) return false
        return engineType == other.engineType
    }

    override fun hashCode(): Int = engineType.hashCode()
}
