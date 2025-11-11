package com.ead.katalyst.ktor.engine.cio

import com.ead.katalyst.ktor.engine.KtorEngineFactory
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import org.slf4j.LoggerFactory

/**
 * Factory for creating CIO (Coroutine-based I/O) backed Ktor servers.
 *
 * This factory encapsulates all CIO-specific server creation logic,
 * allowing the framework to create CIO servers without hardcoding
 * CIO dependencies in the bootstrap layer.
 *
 * The factory respects configuration from ServerConfiguration, enabling
 * runtime engine selection and configuration without code changes.
 */
class CioEngineFactory(
    private val config: CioEngineConfiguration
) : KtorEngineFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(CioEngineFactory::class.java)
    }

    override val engineType: String = "cio"

    override fun createServer(
        host: String,
        port: Int,
        connectingIdleTimeoutMs: Long,
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
        logger.info(
            "Creating CIO server: host=$host, port=$port, " +
            "connectionIdleTimeoutMs=$connectingIdleTimeoutMs"
        )

        return try {
            embeddedServer(
                factory = CIO,
                host = host,
                port = port
            ) {
                block()
            }
        } catch (e: Exception) {
            logger.error("Failed to create CIO server", e)
            throw IllegalStateException("Failed to create CIO embedded server", e)
        }
    }
}
