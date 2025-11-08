package com.ead.katalyst.messaging.amqp

/**
 * Configuration for AMQP/RabbitMQ messaging.
 *
 * Encapsulates all settings needed for AMQP connection, queue setup, and behavior.
 *
 * **Usage:**
 *
 * ```kotlin
 * val config = AmqpConfiguration(
 *     host = "localhost",
 *     port = 5672,
 *     username = "guest",
 *     password = "guest",
 *     virtualHost = "/"
 * )
 *
 * val amqpClient = AmqpConnection(config)
 * ```
 *
 * @property host RabbitMQ server hostname
 * @property port RabbitMQ server port (default: 5672)
 * @property username Connection username
 * @property password Connection password
 * @property virtualHost RabbitMQ virtual host (default: /)
 * @property exchangeName Exchange for events (default: "events")
 * @property exchangeType Exchange type: "direct", "topic", "fanout" (default: "topic")
 * @property durable Whether to persist configuration (default: true)
 * @property autoDelete Whether to delete when not in use (default: false)
 * @property connectionTimeoutMs Connection timeout in milliseconds (default: 10000)
 * @property enableDeadLetterQueue Whether to create DLQ (default: true)
 * @property dlqPrefix Prefix for dead letter queues (default: "dlq.")
 * @property maxRetries Maximum message retries before DLQ (default: 3)
 */
data class AmqpConfiguration(
    val host: String = "localhost",
    val port: Int = 5672,
    val username: String = "guest",
    val password: String = "guest",
    val virtualHost: String = "/",
    val exchangeName: String = "events",
    val exchangeType: String = "topic",
    val durable: Boolean = true,
    val autoDelete: Boolean = false,
    val connectionTimeoutMs: Int = 10000,
    val enableDeadLetterQueue: Boolean = true,
    val dlqPrefix: String = "dlq.",
    val maxRetries: Int = 3
) {
    /**
     * Create a copy with updated values.
     */
    fun withHost(host: String): AmqpConfiguration = copy(host = host)

    fun withPort(port: Int): AmqpConfiguration = copy(port = port)

    fun withCredentials(username: String, password: String): AmqpConfiguration =
        copy(username = username, password = password)

    fun withVirtualHost(virtualHost: String): AmqpConfiguration = copy(virtualHost = virtualHost)

    fun withExchangeName(name: String): AmqpConfiguration = copy(exchangeName = name)

    fun withExchangeType(type: String): AmqpConfiguration = copy(exchangeType = type)

    fun withDurable(durable: Boolean): AmqpConfiguration = copy(durable = durable)

    fun withAutoDelete(autoDelete: Boolean): AmqpConfiguration = copy(autoDelete = autoDelete)

    fun withConnectionTimeout(timeoutMs: Int): AmqpConfiguration = copy(connectionTimeoutMs = timeoutMs)

    fun withDeadLetterQueue(enabled: Boolean): AmqpConfiguration = copy(enableDeadLetterQueue = enabled)

    fun withDlqPrefix(prefix: String): AmqpConfiguration = copy(dlqPrefix = prefix)

    fun withMaxRetries(maxRetries: Int): AmqpConfiguration = copy(maxRetries = maxRetries)

    /**
     * Validate configuration for consistency.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be between 1 and 65535" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
        require(exchangeName.isNotBlank()) { "exchangeName must not be blank" }
        require(exchangeType in listOf("direct", "topic", "fanout")) { "exchangeType must be 'direct', 'topic', or 'fanout'" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be > 0" }
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
    }

    companion object {
        /**
         * Create default local development configuration.
         */
        fun local(): AmqpConfiguration = AmqpConfiguration()

        /**
         * Create configuration for testing with test container.
         */
        fun testContainer(host: String = "localhost", port: Int = 5672): AmqpConfiguration =
            AmqpConfiguration(
                host = host,
                port = port,
                connectionTimeoutMs = 5000
            )

        /**
         * Create configuration for production with SSL/TLS.
         */
        fun production(host: String, username: String, password: String): AmqpConfiguration =
            AmqpConfiguration(
                host = host,
                port = 5671,
                username = username,
                password = password,
                connectionTimeoutMs = 30000,
                durable = true,
                autoDelete = false
            )
    }
}
