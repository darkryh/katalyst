package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.ktor.engine.KtorEngineFactory
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

/**
 * Factory for creating Netty-backed Ktor servers.
 *
 * This factory encapsulates all Netty-specific server creation logic,
 * allowing the framework to create Netty servers without hardcoding
 * Netty dependencies in the bootstrap layer.
 *
 * The factory respects configuration from ServerConfiguration, enabling
 * runtime engine selection and configuration without code changes.
 */
class NettyEngineFactory(
    private val config: NettyEngineConfiguration
) : KtorEngineFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(NettyEngineFactory::class.java)
    }

    override val engineType: String = "netty"

    override fun createServer(
        host: String,
        port: Int,
        connectingIdleTimeoutMs: Long,
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
        logger.info(
            "Creating Netty server: host=$host, port=$port, " +
            "workerThreads=${config.workerThreads}, " +
            "connectionIdleTimeoutMs=$connectingIdleTimeoutMs"
        )

        return try {
            embeddedServer(
                factory = Netty,
                host = host,
                port = port
            ) {
                block()
            }
        } catch (e: Exception) {
            logger.error("Failed to create Netty server", e)
            throw IllegalStateException("Failed to create Netty embedded server", e)
        }
    }
}
