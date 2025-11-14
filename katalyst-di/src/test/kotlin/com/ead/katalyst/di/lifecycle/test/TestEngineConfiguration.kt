package com.ead.katalyst.di.lifecycle.test

import com.ead.katalyst.ktor.engine.KatalystKtorEngine

/**
 * Test fixture for KatalystKtorEngine.
 *
 * Allows tests to provide a mock engine object for testing
 * engine selection and DI injection without requiring actual Ktor engine setup.
 */
class TestEngine(
    override val engineType: String = "test"
) : KatalystKtorEngine {
    override fun toString(): String {
        return "TestEngine(engineType='$engineType')"
    }
}

/**
 * Test fixture for engine configuration.
 *
 * Allows tests to create engine configurations with custom host/port
 * without needing actual Ktor server setup.
 */
class TestEngineConfiguration(
    val host: String = "localhost",
    val port: Int = 8080
) {

    override fun toString(): String {
        return "TestEngineConfiguration(host='$host', port=$port)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestEngineConfiguration) return false
        return host == other.host && port == other.port
    }

    override fun hashCode(): Int {
        return host.hashCode() * 31 + port
    }
}
