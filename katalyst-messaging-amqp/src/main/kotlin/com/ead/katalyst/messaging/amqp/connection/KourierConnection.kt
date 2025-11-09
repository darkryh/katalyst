package com.ead.katalyst.messaging.amqp.connection

import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import dev.kourier.amqp.robust.RobustAMQPChannel
import dev.kourier.amqp.robust.RobustAMQPConnection
import dev.kourier.amqp.robust.createRobustAMQPConnection
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory

/**
 * Manages AMQP connection lifecycle using pure Kotlin Kourier client.
 *
 * Key advantages over RabbitMQ Java Client:
 * - All operations are suspend functions (true async/await)
 * - Automatic recovery and reconnection built-in
 * - Non-blocking I/O (async sockets, not thread pools)
 * - Resource management via CoroutineScope
 * - Memory efficient (async I/O, not threads)
 *
 * **Usage:**
 *
 * ```kotlin
 * val connection = KourierConnection(config, coroutineScope)
 *
 * // Connect (suspend function)
 * connection.connect()
 *
 * // Create channel (suspend function)
 * val channel = connection.createChannel()
 *
 * // Clean up
 * connection.close()
 * ```
 *
 * @param config AMQP configuration
 * @param scope CoroutineScope for managing async operations
 */
class KourierConnection(
    private val config: AmqpConfiguration,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(KourierConnection::class.java)
    private var connection: RobustAMQPConnection? = null

    /**
     * Establish connection with automatic recovery.
     *
     * This is a suspend function - does NOT block threads.
     * Uses Kourier's robust client which handles:
     * - Network failure recovery
     * - Channel recovery
     * - Consumer recovery
     * - Queue/exchange redeclaration
     *
     * @return Connection instance
     * @throws AmqpConnectionException if connection fails
     */
    suspend fun connect(): RobustAMQPConnection {
        if (connection != null) {
            return connection!!
        }

        logger.info(
            "Connecting to AMQP server at {}:{}/{}",
            config.host,
            config.port,
            config.virtualHost
        )

        return try {
            // Build connection URL from configuration
            val connUrl = buildConnectionUrl()

            // Create robust connection (with automatic recovery)
            val conn = createRobustAMQPConnection(scope, connUrl) as RobustAMQPConnection

            connection = conn
            logger.info("✅ Successfully connected to AMQP server (automatic recovery enabled)")
            conn
        } catch (e: Exception) {
            logger.error("Failed to connect to AMQP server: {}", e.message, e)
            throw AmqpConnectionException("Failed to connect: ${e.message}", e)
        }
    }

    /**
     * Build AMQP connection URL from configuration.
     *
     * Format: amqp://username:password@host:port/virtualhost
     */
    private fun buildConnectionUrl(): String {
        val userPass = "${config.username}:${config.password}"
        val hostPort = "${config.host}:${config.port}"
        return "amqp://$userPass@$hostPort/${config.virtualHost}"
    }

    /**
     * Get existing connection or throw if not connected.
     *
     * Use this only when you know connection is already established.
     * For normal operations, use connect() or createChannel().
     *
     * @return Current connection
     * @throws IllegalStateException if not connected
     */
    fun getConnection(): RobustAMQPConnection {
        return connection ?: throw IllegalStateException(
            "Connection not established. Call connect() first."
        )
    }

    /**
     * Create a new channel from the connection (suspend function).
     *
     * Channels are lightweight and should be created as needed.
     * Each channel has its own state and can be used independently.
     *
     * @return New channel for publish/consume operations
     * @throws AmqpConnectionException if channel creation fails
     */
    suspend fun createChannel(): RobustAMQPChannel {
        return try {
            val connection = getConnection()
            connection.openChannel() as RobustAMQPChannel
        } catch (e: Exception) {
            logger.error("Failed to create channel: {}", e.message, e)
            throw AmqpConnectionException("Failed to create channel: ${e.message}", e)
        }
    }

    /**
     * Check if currently connected.
     *
     * Note: Connection might be automatically recovering if false.
     * Kourier's robust client handles reconnection transparently.
     *
     * @return True if connection is established
     */
    fun isConnected(): Boolean = connection != null

    /**
     * Close connection gracefully.
     *
     * This is a suspend function.
     * Waits for all pending operations to complete before closing.
     */
    suspend fun close() {
        try {
            connection?.close()
            connection = null
            logger.info("✅ Connection closed")
        } catch (e: Exception) {
            logger.error("Error closing AMQP connection: {}", e.message, e)
        }
    }

    /**
     * Force close connection immediately (for emergencies).
     *
     * This is a suspend function.
     * Use close() for normal shutdown, forceClose() only when necessary.
     */
    suspend fun forceClose() {
        try {
            connection?.close()
            connection = null
            logger.info("✅ Connection force closed")
        } catch (e: Exception) {
            logger.error("Error force closing: {}", e.message, e)
        }
    }
}

/**
 * Exception thrown when AMQP connection operations fail.
 *
 * @param message Error message
 * @param cause Underlying exception
 */
class AmqpConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)