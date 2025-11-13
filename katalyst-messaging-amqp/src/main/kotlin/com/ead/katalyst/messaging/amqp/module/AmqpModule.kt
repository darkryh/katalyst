package com.ead.katalyst.messaging.amqp.module

import com.ead.katalyst.client.EventClientInterceptor
import com.ead.katalyst.client.GlobalEventClientInterceptorRegistry
import com.ead.katalyst.events.transport.serialization.JsonEventSerializer
import com.ead.katalyst.messaging.amqp.AmqpEventBridge
import com.ead.katalyst.messaging.amqp.consumer.KourierDeadLetterQueueHandler
import com.ead.katalyst.messaging.amqp.publisher.KourierPublisher
import com.ead.katalyst.messaging.amqp.config.AmqpConfiguration
import com.ead.katalyst.messaging.amqp.connection.AmqpConnectionException
import com.ead.katalyst.messaging.amqp.connection.KourierConnection
import com.ead.katalyst.messaging.amqp.consumer.KourierConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin DI module for katalyst-messaging-amqp.
 *
 * Registers AMQP/RabbitMQ components and optionally bridges to EventClient.
 * Enables event publishing to RabbitMQ for external service consumption.
 *
 * **Features:**
 * - Automatic connection management with recovery
 * - Publisher and consumer registration
 * - Dead letter queue handling
 * - Optional EventClient integration via interceptor
 * - Fluent configuration
 *
 * **Basic Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         // ... other modules ...
 *         amqpModule()
 *     )
 * }
 *
 * val publisher: AmqpPublisher = get()
 * val consumer: AmqpConsumer = get()
 * ```
 *
 * **With Custom Configuration:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         amqpModule(
 *             config = AmqpConfiguration(
 *                 host = "rabbitmq.example.com",
 *                 port = 5672,
 *                 username = "app",
 *                 password = "secret"
 *             ),
 *             enableEventBridge = true
 *         )
 *     )
 * }
 * ```
 *
 * **With EventClient Integration:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         eventsCoreModule,
 *         eventsBusModule,
 *         eventsTransportModule,
 *         eventsClientModule(),
 *         amqpModule(enableEventBridge = true)  // Auto-forward events to AMQP
 *     )
 * }
 *
 * val eventClient: EventClient = get()
 * eventClient.publish(userCreatedEvent)  // Automatically goes to RabbitMQ
 * ```
 *
 * @param config AMQP configuration (default: local development)
 * @param enableEventBridge Whether to register AmqpEventBridge as EventClientInterceptor
 * @param routingKeyPrefix Prefix for AMQP routing keys (default: "events")
 */
fun amqpModule(
    config: AmqpConfiguration = AmqpConfiguration.local(),
    enableEventBridge: Boolean = false,
    routingKeyPrefix: String = "events"
): Module = module {
    val logger = LoggerFactory.getLogger("AmqpModule")

    // Validate configuration
    try {
        config.validate()
        logger.info("Configuring AMQP with: host=${config.host}, port=${config.port}, " +
                "exchange=${config.exchangeName}, dlq=${config.enableDeadLetterQueue}")
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid AMQP configuration: {}", e.message)
        throw e
    }

    // Register configuration as singleton
    single<AmqpConfiguration> { config }

    // Register coroutine scope for AMQP operations
    single<CoroutineScope> {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    // Register Kourier connection as singleton with automatic recovery
    single<KourierConnection> {
        val connection = KourierConnection(
            config = get<AmqpConfiguration>(),
            scope = get<CoroutineScope>()
        )
        // Establish connection immediately to fail fast if unavailable
        try {
            // Note: This would need to be called from a suspend context
            // For now, we'll rely on lazy connection establishment
            logger.info("KourierConnection registered with auto-recovery enabled")
        } catch (e: Exception) {
            logger.error("Failed to initialize Kourier connection: {}", e.message, e)
            throw AmqpConnectionException("Failed to initialize AMQP connection: ${e.message}", e)
        }
        connection
    }

    // Register Kourier publisher as singleton
    single<KourierPublisher> {
        KourierPublisher(
            config = get<AmqpConfiguration>(),
            connection = get<KourierConnection>()
        ).also {
            logger.debug("KourierPublisher registered")
        }
    }

    // Register Kourier consumer factory (create new instances on demand)
    factory<KourierConsumer> {
        KourierConsumer(
            config = get<AmqpConfiguration>(),
            connection = get<KourierConnection>(),
            scope = get<CoroutineScope>()
        )
    }

    // Register Kourier DLQ handler as singleton
    single<KourierDeadLetterQueueHandler> {
        KourierDeadLetterQueueHandler(
            config = get<AmqpConfiguration>(),
            connection = get<KourierConnection>(),
            scope = get<CoroutineScope>()
        ).also {
            logger.debug("KourierDeadLetterQueueHandler registered")
        }
    }

    // Optionally register AMQP event bridge as interceptor
    if (enableEventBridge) {
        single {
            val serializer = getOrNull<JsonEventSerializer>()
                ?: throw IllegalStateException(
                    "JsonEventSerializer not found in Koin context. " +
                    "Ensure eventsTransportModule is loaded before amqpModule with enableEventBridge=true"
                )

            AmqpEventBridge(
                publisher = get<KourierPublisher>(),
                serializer = serializer,
                routingKeyPrefix = routingKeyPrefix
            ).also {
                logger.info("AmqpEventBridge registered (Kourier-based) and will intercept EventClient publishes")
                GlobalEventClientInterceptorRegistry.register(it)
            }
        } bind EventClientInterceptor::class

        // Note: The bridge can be added to EventClient's interceptor list via:
        // val bridge = koin.get<AmqpEventBridge>()
        // OR in EventClientConfiguration.addInterceptor(bridge)
        // This allows flexible integration depending on application needs
    }
}

/**
 * Extension function to load AMQP module with default configuration.
 *
 * **Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         // ... other modules ...
 *         amqpModuleDefault()
 *     )
 * }
 * ```
 */
fun amqpModuleDefault(): Module = amqpModule()

/**
 * Preset configurations for common AMQP use cases.
 */
object AmqpPresets {
    /**
     * Local development configuration.
     *
     * Connects to localhost:5672 with guest credentials.
     */
    fun development(): Module = amqpModule(
        config = AmqpConfiguration.local(),
        enableEventBridge = false
    )

    /**
     * Production configuration.
     *
     * Uses environment variables for credentials and enables event bridge.
     *
     * **Environment Variables:**
     * - AMQP_HOST: RabbitMQ host
     * - AMQP_PORT: RabbitMQ port (default: 5671 for SSL)
     * - AMQP_USERNAME: Connection username
     * - AMQP_PASSWORD: Connection password
     *
     * **Usage:**
     *
     * ```kotlin
     * startKoin {
     *     modules(
     *         // ... other modules ...
     *         AmqpPresets.production()
     *     )
     * }
     * ```
     */
    fun production(): Module {
        val host = System.getenv("AMQP_HOST") ?: "rabbitmq.default.svc.cluster.local"
        val port = System.getenv("AMQP_PORT")?.toIntOrNull() ?: 5671
        val username = System.getenv("AMQP_USERNAME") ?: "admin"
        val password = System.getenv("AMQP_PASSWORD") ?: "admin"

        return amqpModule(
            config = AmqpConfiguration.production(
                host = host,
                username = username,
                password = password
            ).withPort(port),
            enableEventBridge = true,
            routingKeyPrefix = "prod.events"
        )
    }

    /**
     * Testing configuration with testcontainer.
     *
     * Connects to testcontainer RabbitMQ instance.
     *
     * **Usage:**
     *
     * ```kotlin
     * @Container
     * val rabbitContainer = GenericContainer("rabbitmq:latest")
     *     .withExposedPorts(5672)
     *
     * startKoin {
     *     modules(
     *         AmqpPresets.testing(
     *             host = rabbitContainer.host,
     *             port = rabbitContainer.getMappedPort(5672)
     *         )
     *     )
     * }
     * ```
     */
    fun testing(host: String = "localhost", port: Int = 5672): Module = amqpModule(
        config = AmqpConfiguration.testContainer(host, port),
        enableEventBridge = false
    )

    /**
     * High-throughput configuration for heavy event load.
     *
     * Uses larger batches and durable queues.
     */
    fun highThroughput(): Module = amqpModule(
        config = AmqpConfiguration.local()
            .withExchangeType("topic")
            .withDurable(true)
            .withDeadLetterQueue(true),
        enableEventBridge = true
    )

    /**
     * High-reliability configuration with strict DLQ handling.
     *
     * Enables all safety features and retries.
     */
    fun highReliability(): Module = amqpModule(
        config = AmqpConfiguration.local()
            .withDurable(true)
            .withDeadLetterQueue(true)
            .withMaxRetries(5)
            .withConnectionTimeout(30000),
        enableEventBridge = true
    )
}

/**
 * Exception thrown when AMQP module initialization fails.
 *
 * @param message Error message
 * @param cause Underlying exception
 */
class AmqpModuleException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
