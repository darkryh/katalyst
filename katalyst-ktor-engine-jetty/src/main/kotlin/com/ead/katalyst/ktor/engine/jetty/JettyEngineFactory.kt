package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.ktor.engine.KtorEngineFactory
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import org.slf4j.LoggerFactory

/**
 * Factory for creating Jetty-backed Ktor servers.
 *
 * This factory encapsulates all Jetty-specific server creation logic,
 * allowing the framework to create Jetty servers without hardcoding
 * Jetty dependencies in the bootstrap layer.
 *
 * The factory respects configuration from ServerConfiguration, enabling
 * runtime engine selection and configuration without code changes.
 */
class JettyEngineFactory(
    private val config: JettyEngineConfiguration
) : KtorEngineFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(JettyEngineFactory::class.java)
    }

    override val engineType: String = "jetty"

    override fun createServer(
        host: String,
        port: Int,
        connectingIdleTimeoutMs: Long,
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
        logger.info(
            "Creating Jetty server: host=$host, port=$port, " +
            "maxThreads=${config.maxThreads}, minThreads=${config.minThreads}, " +
            "connectionIdleTimeoutMs=$connectingIdleTimeoutMs"
        )

        return try {
            embeddedServer(
                factory = Jetty,
                host = host,
                port = port
            ) {
                block()
            }
        } catch (e: Exception) {
            logger.error("Failed to create Jetty server", e)
            throw IllegalStateException("Failed to create Jetty embedded server", e)
        }
    }
}
