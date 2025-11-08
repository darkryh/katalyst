// ============================================================================
// SIMPLE EXAMPLE: Choosing Messaging Backends at Application Level
// ============================================================================

import com.ead.katalyst.client.EventClient
import com.ead.katalyst.client.EventClientInterceptor
import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.example.domain.events.UserCreatedEvent
import com.ead.katalyst.messaging.amqp.AmqpConfiguration
import org.slf4j.LoggerFactory

// ============================================================================
// SCENARIO 1: Development - Local Events Only (No RabbitMQ)
// ============================================================================

/**
 * Development mode: Events stay local
 *
 * Use when:
 * - Developing locally
 * - Testing features
 * - No RabbitMQ server available
 *
 * Result: Events publish to local EventBus, no external messaging
 */
fun developmentApp(args: Array<String>) = katalystApplication(args) {
    database(/* ... */)
    scanPackages("com.ead.katalyst.example")
    enableScheduler()

    enableEvents {
        // Event system enabled
    }

    // ❌ NO RabbitMQ
    // ❌ NO external messaging
    // ✅ Local EventBus only
}

// Usage in service - EXACTLY THE SAME:
class DevUserService(private val eventClient: EventClient) {
    suspend fun createUser(name: String, email: String) {
        // This event publishes to local EventBus
        // No RabbitMQ involved
        eventClient.publish(UserCreatedEvent(
            userId = 1L,
            email = email
        ))
    }
}

// ============================================================================
// SCENARIO 2: Production with RabbitMQ
// ============================================================================

/**
 * Production mode: Events go to RabbitMQ
 *
 * Use when:
 * - Multiple services need to receive events
 * - Events need to be durable/persistent
 * - Cross-service communication needed
 *
 * Result: Events publish locally AND to RabbitMQ
 */
fun productionApp(args: Array<String>) = katalystApplication(args) {
    database(/* ... */)
    scanPackages("com.ead.katalyst.example")
    enableScheduler()

    enableEvents {
        // Event system enabled
    }

    // ✅ Enable RabbitMQ module
    enableAMQP {
        enableEventBridge = true  // Register AmqpEventBridge as interceptor
        config = AmqpConfiguration.production(
            host = System.getenv("AMQP_HOST") ?: "rabbitmq.default.svc.cluster.local",
            username = System.getenv("AMQP_USERNAME") ?: "admin",
            password = System.getenv("AMQP_PASSWORD") ?: "admin"
        )
    }
}

// Usage in service - EXACTLY THE SAME:
class ProdUserService(private val eventClient: EventClient) {
    suspend fun createUser(name: String, email: String) {
        // This event publishes to:
        // 1. Local EventBus (internal handlers)
        // 2. RabbitMQ (external systems via AmqpEventBridge)
        eventClient.publish(UserCreatedEvent(
            userId = 1L,
            email = email
        ))
    }
}

// ============================================================================
// SCENARIO 3: Custom Logging Interceptor (Works with any backend)
// ============================================================================

/**
 * Custom interceptor that logs all events
 * Works in dev (local-only) and prod (RabbitMQ)
 */
class LoggingInterceptor : EventClientInterceptor {
    private val logger = LoggerFactory.getLogger(LoggingInterceptor::class.java)

    override suspend fun beforePublish(
        event: DomainEvent,
        context: EventClientInterceptor.PublishContext
    ): EventClientInterceptor.InterceptResult {
        logger.info("📤 Publishing event: type={}, id={}",
            event::class.simpleName, context.eventId)
        return EventClientInterceptor.InterceptResult.Continue
    }

    override suspend fun afterPublish(
        event: DomainEvent,
        result: com.ead.katalyst.client.PublishResult,
        context: EventClientInterceptor.PublishContext,
        durationMs: Long
    ) {
        when (result) {
            is com.ead.katalyst.client.PublishResult.Success ->
                logger.info("✅ Event published successfully: {}", context.eventId)
            is com.ead.katalyst.client.PublishResult.Failure ->
                logger.error("❌ Event failed: {} - {}", context.eventId, result.reason)
            else -> {}
        }
    }
}

// ============================================================================
// SCENARIO 4: Conditional App Based on Environment
// ============================================================================

fun main(args: Array<String>) {
    val env = System.getenv("APP_ENV") ?: "dev"

    println("🚀 Starting application in $env mode")

    when (env) {
        "dev" -> developmentApp(args)
        "prod" -> productionApp(args)
        else -> developmentApp(args)
    }
}

// ============================================================================
// KEY INSIGHT
// ============================================================================

/*
The EventClient is an ABSTRACTION - your service code is IDENTICAL
whether you use local events or RabbitMQ!

┌─────────────────────────────────────────────────────────────┐
│ Service Code (NEVER CHANGES)                                │
├─────────────────────────────────────────────────────────────┤
│ class UserService(eventClient: EventClient) {               │
│     eventClient.publish(UserCreatedEvent(...))              │
│ }                                                            │
└─────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────┐
│ Application Config (CHANGES per environment)                │
├─────────────────────────────────────────────────────────────┤
│ Dev:  enableAMQP { } omitted → Local only                   │
│ Prod: enableAMQP { enableEventBridge = true } → RabbitMQ    │
└─────────────────────────────────────────────────────────────┘

This is the POWER OF ABSTRACTION!
You get flexibility without coupling your business logic to infrastructure.
*/

// ============================================================================
// COMPARISON TABLE
// ============================================================================

/*
┌──────────────┬─────────────────────┬────────────────────────┐
│ Configuration│ Dev Mode            │ Production Mode        │
├──────────────┼─────────────────────┼────────────────────────┤
│ enableAMQP   │ NO (omitted)        │ YES with credentials   │
│ External msg │ Disabled            │ Enabled (RabbitMQ)     │
│ Local bus    │ Enabled             │ Enabled                │
│ Service code │ SAME                │ SAME                   │
│ Interceptors │ LoggingInterceptor  │ LoggingInterceptor +   │
│              │                     │ MetricsInterceptor +   │
│              │                     │ AmqpEventBridge        │
├──────────────┼─────────────────────┼────────────────────────┤
│ User event   │ Publishes to        │ Publishes to           │
│ flow         │ LocalBus            │ LocalBus + RabbitMQ    │
│              │ ↓                   │ ↓        ↓             │
│              │ @EventHandler       │ Handlers + External    │
│              │ methods inside app  │ services (other apps)  │
└──────────────┴─────────────────────┴────────────────────────┘
*/
