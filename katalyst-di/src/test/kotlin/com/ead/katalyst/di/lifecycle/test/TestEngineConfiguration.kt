package com.ead.katalyst.di.lifecycle.test

import com.ead.katalyst.ktor.engine.KtorEngineConfiguration
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer

/**
 * Test fixture for KtorEngineConfiguration.
 *
 * Allows tests to provide a mock engine configuration without
 * requiring actual Ktor engine setup.
 */
class TestEngineConfiguration(
    override val host: String = "localhost",
    override val port: Int = 8080
) : KtorEngineConfiguration {
    override fun createServer(
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
        throw UnsupportedOperationException(
            "TestEngineConfiguration does not support createServer(). " +
            "This is a test fixture only."
        )
    }

    override fun toString(): String {
        return "TestEngineConfiguration(host='$host', port=$port)"
    }
}
