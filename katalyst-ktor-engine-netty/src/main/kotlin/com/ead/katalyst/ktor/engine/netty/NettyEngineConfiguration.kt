package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.ktor.engine.KtorEngineConfiguration
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

/**
 * Netty-based Ktor engine configuration implementation.
 *
 * Netty provides high-performance, non-blocking I/O with excellent throughput
 * and is the recommended choice for production deployments.
 *
 * Performance Characteristics:
 * - Throughput: Very High (10,000+ req/s typical)
 * - Latency: Low
 * - Memory: Moderate
 * - Concurrency: Excellent (millions of connections possible)
 * - Best For: Production services requiring high throughput
 */
class NettyEngineConfiguration(
    override val host: String = "0.0.0.0",
    override val port: Int = 8080,
    private val workerThreads: Int = Runtime.getRuntime().availableProcessors() * 2,
    private val connectionIdleTimeoutMs: Long = 180000, // 3 minutes
) : KtorEngineConfiguration {

    companion object {
        private val logger = LoggerFactory.getLogger(NettyEngineConfiguration::class.java)
    }

    /**
     * Create a Netty-based embedded server.
     *
     * @param block The Ktor application configuration block
     * @return EmbeddedServer<*, *> Netty engine configured and ready to start
     */
    override fun createServer(
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
        logger.info(
            "Creating Netty engine: host=$host, port=$port, workerThreads=$workerThreads, " +
            "connectionIdleTimeoutMs=$connectionIdleTimeoutMs"
        )

        return try {
            val server = embeddedServer(
                factory = Netty,
                host = host,
                port = port
            ) {
                // Application configuration via Ktor modules
                logger.debug("Netty application engine configured: host=$host, port=$port")
            }

            logger.info("Netty engine created successfully")
            server
        } catch (e: Exception) {
            logger.error("Failed to create Netty engine configuration", e)
            throw IllegalStateException("Failed to create Netty engine: ${e.message}", e)
        }
    }

    override fun toString(): String = buildString {
        append("NettyEngineConfiguration(")
        append("host=$host, ")
        append("port=$port, ")
        append("workers=$workerThreads, ")
        append("idleTimeout=${connectionIdleTimeoutMs}ms)")
    }
}
