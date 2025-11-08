# EventClient Messaging Abstraction Example

The `EventClient` is an **abstraction** that allows you to choose which messaging backend to use at **application startup**. This gives you complete flexibility.

## Architecture Overview

```
UserService
    ↓
EventClient (abstraction interface)
    ↓
Interceptors (pluggable implementations):
    ├─ AmqpEventBridge     (RabbitMQ via Kourier)
    ├─ LoggingInterceptor  (Console/File logging)
    ├─ KafkaEventBridge    (Apache Kafka)
    ├─ HttpEventBridge     (HTTP webhooks)
    └─ ... any custom implementation
```

**Key Insight:** Your service code uses `EventClient` (abstraction), but **which backend actually handles events depends on which interceptors you register at startup**.

---

## Example 1: Development Mode (In-Memory, No RabbitMQ)

```kotlin
// Development configuration - local events only, no external messaging
fun applicationDev(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.config())
    scanPackages("com.ead.katalyst.example")
    enableScheduler()

    enableEvents {
        // All enabled by default
    }

    // ✅ Development: Use only local event bus
    // ❌ NO RabbitMQ, NO external messaging
    configureEventClient {
        publishToLocalBus(true)      // Enable local bus
        publishToExternal(false)     // Disable external messaging

        // Add only logging interceptor
        addInterceptor(LoggingInterceptor())
    }
}
```

**Behavior:**
- Events publish to local EventBus
- Internal @EventHandler methods receive events
- No RabbitMQ connection needed
- Perfect for local development/testing

---

## Example 2: Production Mode (RabbitMQ)

```kotlin
// Production configuration - RabbitMQ messaging
fun applicationProd(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.config())
    scanPackages("com.ead.katalyst.example")
    enableScheduler()

    enableEvents {
        // All enabled by default
    }

    // ✅ Production: Use RabbitMQ via AMQP
    // ✅ Add AMQP messaging module
    enableAMQP {
        enableEventBridge = true  // Register AmqpEventBridge as interceptor
        config = AmqpConfiguration.production(
            host = System.getenv("AMQP_HOST") ?: "localhost",
            username = System.getenv("AMQP_USERNAME") ?: "guest",
            password = System.getenv("AMQP_PASSWORD") ?: "guest"
        )
    }

    // Configure EventClient to use RabbitMQ
    configureEventClient {
        publishToLocalBus(true)      // Enable local bus (internal handlers)
        publishToExternal(true)      // Enable external (RabbitMQ)

        // Add retry policy for RabbitMQ failures
        retryPolicy(RetryPolicy.exponentialBackoff(
            initialDelayMs = 100,
            maxDelayMs = 30000,
            maxAttempts = 3
        ))

        // Metrics and logging interceptors
        addInterceptor(MetricsInterceptor())
        addInterceptor(LoggingInterceptor())
        // AmqpEventBridge already registered by enableAMQP
    }
}
```

**Behavior:**
- Events publish to local EventBus AND RabbitMQ
- Internal handlers + external systems receive events
- Full resilience with retries
- Distributed system ready

---

## Example 3: Hybrid (Local + RabbitMQ)

```kotlin
fun applicationHybrid(args: Array<String>) = katalystApplication(args) {
    // ... config ...

    enableAMQP {
        enableEventBridge = true  // RabbitMQ
    }

    configureEventClient {
        // Both local and external enabled
        publishToLocalBus(true)
        publishToExternal(true)

        // Add multiple interceptors
        addInterceptor(LoggingInterceptor())
        addInterceptor(MetricsInterceptor())
        addInterceptor(ValidationInterceptor())
        // AmqpEventBridge auto-included
    }
}
```

---

## Example 4: Custom Implementation - Kafka Backend

```kotlin
// Create your own interceptor for Kafka
class KafkaEventBridge(
    private val kafkaProducer: KafkaProducer<String, String>
) : EventClientInterceptor {

    override suspend fun afterPublish(
        event: DomainEvent,
        result: PublishResult,
        context: EventClientInterceptor.PublishContext
    ) {
        if (result.isSuccess()) {
            // Serialize and send to Kafka
            val json = Json.encodeToString(event)
            kafkaProducer.send(
                ProducerRecord(
                    "events-topic",
                    event.eventType(),
                    json
                )
            )
        }
    }
}

// Use it at startup
fun applicationWithKafka(args: Array<String>) = katalystApplication(args) {
    // ... config ...

    configureEventClient {
        publishToLocalBus(true)
        publishToExternal(true)

        // Use Kafka instead of RabbitMQ
        addInterceptor(KafkaEventBridge(kafkaProducer))
        addInterceptor(LoggingInterceptor())
    }
}
```

---

## Example 5: Service Code (Unchanged Across All Modes!)

```kotlin
// UserService - SAME CODE for dev, prod, hybrid, Kafka, etc.
class UserService(
    private val userRepository: UserRepository,
    private val eventClient: EventClient  // Always the same abstraction!
) : Service {

    suspend fun createUser(request: CreateUserRequest): User =
        transactionManager.transaction {
            val user = userRepository.save(...)

            // This works the same in dev, prod, with RabbitMQ, Kafka, etc.
            eventClient.publish(UserCreatedEvent(
                userId = user.id,
                email = user.email
            ))

            return@transaction user
        }
}
```

**Key Point:** Service code never changes! Only application startup configuration changes.

---

## Example 6: Conditional Configuration by Environment

```kotlin
fun main(args: Array<String>) {
    val environment = System.getenv("ENVIRONMENT") ?: "dev"

    when (environment) {
        "dev" -> applicationDev(args)
        "staging" -> applicationStaging(args)
        "prod" -> applicationProd(args)
        else -> applicationDev(args)
    }
}

// Development: Local events only
fun applicationDev(args: Array<String>) = katalystApplication(args) {
    // ...
    configureEventClient {
        publishToLocalBus(true)
        publishToExternal(false)  // ← NO external messaging
        addInterceptor(LoggingInterceptor())
    }
}

// Staging: RabbitMQ with test credentials
fun applicationStaging(args: Array<String>) = katalystApplication(args) {
    // ...
    enableAMQP {
        enableEventBridge = true
        config = AmqpConfiguration(
            host = "rabbitmq-staging.internal",
            port = 5672,
            username = "staging-user",
            password = "staging-password"
        )
    }

    configureEventClient {
        publishToLocalBus(true)
        publishToExternal(true)
        retryPolicy(RetryPolicy.exponentialBackoff(maxAttempts = 2))
        addInterceptor(LoggingInterceptor())
    }
}

// Production: RabbitMQ with env variables
fun applicationProd(args: Array<String>) = katalystApplication(args) {
    // ...
    enableAMQP {
        enableEventBridge = true
        config = AmqpConfiguration.production(
            host = System.getenv("AMQP_HOST")!!,
            username = System.getenv("AMQP_USERNAME")!!,
            password = System.getenv("AMQP_PASSWORD")!!
        )
    }

    configureEventClient {
        publishToLocalBus(true)
        publishToExternal(true)
        retryPolicy(RetryPolicy.exponentialBackoff(maxAttempts = 5))
        addInterceptor(LoggingInterceptor())
        addInterceptor(MetricsInterceptor())
        addInterceptor(SecurityInterceptor())
    }
}
```

---

## Configuration Options Summary

| Option | Dev | Staging | Prod |
|--------|-----|---------|------|
| `publishToLocalBus(true)` | ✅ | ✅ | ✅ |
| `publishToExternal(false)` | ✅ | ❌ | ❌ |
| RabbitMQ enabled | ❌ | ✅ | ✅ |
| Retry policy | None | 2 attempts | 5 attempts |
| Interceptors | Logging | Logging, Metrics | Logging, Metrics, Security |

---

## Benefits of This Abstraction

✅ **Single Service Code** - Works everywhere without changes
✅ **Environment Flexibility** - Switch backends with just config
✅ **Easy Testing** - Use in-memory mode for tests
✅ **Easy Migration** - Add Kafka? Just add KafkaEventBridge
✅ **No Vendor Lock-in** - Not tied to RabbitMQ
✅ **Zero Code Coupling** - Services don't know about messaging impl

---

## How EventClient Works Under the Hood

```
eventClient.publish(event)
    ↓
beforePublish (all interceptors)
    ↓
Validate event
    ↓
Publish to LocalBus (if enabled)
    ↓
afterPublish (all interceptors)  ← AmqpEventBridge intercepts here
    ↓
AmqpEventBridge.afterPublish sends to RabbitMQ
    ↓
RabbitMQ receives event
    ↓
Other services consume from RabbitMQ
```

Each interceptor can:
- Inspect the event
- Transform it
- Send to external system (RabbitMQ, Kafka, HTTP)
- Log, metrics, security checks
- Retry on failure
