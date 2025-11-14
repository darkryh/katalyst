# Katalyst Testing Best Practices

This document outlines testing conventions, patterns, and best practices for the Katalyst project.

---

## Table of Contents

1. [Test Naming](#test-naming)
2. [Test Structure](#test-structure)
3. [Testing Utilities](#testing-utilities)
4. [Mock Objects](#mock-objects)
5. [Test Data Builders](#test-data-builders)
6. [Async Testing](#async-testing)
7. [Database Testing](#database-testing)
8. [Common Patterns](#common-patterns)

---

## Test Naming

### Convention

Use backtick syntax with natural language descriptions:

**Pattern:** `` `method/scenario should expectedBehavior when condition` ``

### Examples

```kotlin
@Test
fun `save should persist entity and return with ID`()

@Test
fun `findById should return null when entity does not exist`()

@Test
fun `undo should execute operations in LIFO order`()

@Test
fun `publish should throw exception when both local and external disabled`()
```

### Guidelines

- **Start with the method/scenario** being tested
- **Use "should"** to describe expected behavior
- **Add "when" clause** for conditional behavior
- **Be specific** - avoid vague descriptions like "test save" or "it works"
- **Keep it readable** - anyone should understand what the test does

---

## Test Structure

### Given-When-Then Pattern

Organize tests using the Given-When-Then structure with comments:

```kotlin
@Test
fun `exponential backoff should calculate delay as 2^attempt`() {
    // Given
    val policy = ExponentialBackoffRetry(
        baseDelay = 100.milliseconds,
        maxAttempts = 5,
        maxDelay = 10.seconds
    )

    // When
    val delay1 = policy.calculateDelay(attempt = 1)
    val delay2 = policy.calculateDelay(attempt = 2)
    val delay3 = policy.calculateDelay(attempt = 3)

    // Then
    assertEquals(100.milliseconds, delay1)
    assertEquals(200.milliseconds, delay2)
    assertEquals(400.milliseconds, delay3)
}
```

### Structure Elements

- **Given:** Set up test conditions, create objects, configure mocks
- **When:** Execute the operation being tested
- **Then:** Assert expected outcomes

### Single Assertion Principle

Prefer one logical assertion per test. For related assertions, group them:

```kotlin
// Good: Testing one concept with multiple related assertions
@Test
fun `user should have correct properties after creation`() {
    // Given
    val name = "Alice"
    val email = "alice@example.com"

    // When
    val user = User(name, email)

    // Then
    assertEquals(name, user.name)
    assertEquals(email, user.email)
    assertNotNull(user.id)
}

// Avoid: Testing unrelated concepts in one test
@Test
fun `user operations should work`() {
    // Tests creation, update, delete all in one - split these!
}
```

---

## Testing Utilities

### Available Helper Functions

```kotlin
import com.ead.katalyst.testing.core.*

// Unique ID generation
val id = uniqueTestId()           // "test-1234567890"
val email = uniqueEmail()         // "test-1234567890@example.com"

// Exception assertions with message checking
assertThrowsWithMessage<IllegalArgumentException>("Invalid ID") {
    repository.findById(-1)
}

// Collection assertions
assertContainsExactly(actualList, item1, item2, item3)
assertContainsAll(actualList, item1, item2)

// Retry for eventual consistency
val result = retryWithDelay(maxAttempts = 5, delayMillis = 100) {
    database.findById(id) ?: throw NotFoundException()
}
```

### Test Timestamps

```kotlin
import com.ead.katalyst.testing.core.TestTimestamps

// Deterministic timestamps for predictable tests
val createdAt = TestTimestamps.at()              // 2021-01-01 00:00:00
val updatedAt = TestTimestamps.at(3600000)       // 2021-01-01 01:00:00

// Real timestamps for integration tests
val now = TestTimestamps.now()
```

---

## Mock Objects

### Project Pattern: Inline Anonymous Objects

**Katalyst does NOT use Mockk, MockK, or Mockito.** Use inline anonymous objects instead.

### Simple Mock Example

```kotlin
@Test
fun `event client should publish to event bus`() {
    // Given
    val publishedEvents = mutableListOf<DomainEvent>()

    val mockEventBus = object : EventBus {
        override suspend fun publish(event: DomainEvent) {
            publishedEvents.add(event)
        }

        override suspend fun <T : DomainEvent> subscribe(
            eventType: KClass<T>,
            handler: EventHandler<T>
        ) {
            // No-op for this test
        }
    }

    val eventClient = EventClient.builder()
        .localEnabled(true)
        .eventBus(mockEventBus)
        .build()

    val testEvent = TestDomainEvent("test-id")

    // When
    eventClient.publish(testEvent)

    // Then
    assertEquals(1, publishedEvents.size)
    assertEquals(testEvent, publishedEvents.first())
}
```

### In-Memory Repository Mock

```kotlin
class FakeUserRepository : UserRepository {
    private val storage = InMemoryStorage<Long, User>()

    override fun save(user: User): User =
        storage.save(user.id, user)

    override fun findById(id: Long): User? =
        storage.findById(id)

    override fun findAll(): List<User> =
        storage.findAll()

    override fun delete(id: Long): Boolean =
        storage.delete(id)
}
```

### Event Handler Spy

```kotlin
@Test
fun `event bus should notify all subscribers`() {
    // Given
    val handlerSpy = EventHandlerSpy<UserRegisteredEvent>()

    val mockHandler = object : EventHandler<UserRegisteredEvent> {
        override suspend fun handle(event: UserRegisteredEvent) {
            handlerSpy.record(event)
        }
    }

    eventBus.subscribe(UserRegisteredEvent::class, mockHandler)

    // When
    val event = UserRegisteredEvent(userId = 123L)
    eventBus.publish(event)

    // Then
    handlerSpy.assertInvocationCount(1)
    assertEquals(event, handlerSpy.lastEvent)
}
```

---

## Test Data Builders

### Using TestDataBuilder Interface

```kotlin
data class User(val id: Long, val name: String, val email: String, val active: Boolean)

class UserBuilder : TestDataBuilder<User> {
    private var id: Long = TestIdGenerator.nextId()
    private var name: String = "Test User"
    private var email: String = uniqueEmail()
    private var active: Boolean = true

    fun withId(id: Long) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withEmail(email: String) = apply { this.email = email }
    fun inactive() = apply { this.active = false }

    override fun build(): User = User(id, name, email, active)
}

// In tests:
@Test
fun `service should activate inactive users`() {
    // Given
    val inactiveUser = UserBuilder()
        .withName("Alice")
        .inactive()
        .build()

    // When
    val activated = service.activate(inactiveUser.id)

    // Then
    assertTrue(activated.active)
}

// Building multiple instances:
val users = UserBuilder().buildList(10)
```

### Data Class Copy Pattern

For simple cases, use data class `copy()`:

```kotlin
data class TestUser(
    val id: Long = TestIdGenerator.nextId(),
    val name: String = "Test User",
    val email: String = uniqueEmail(),
    val active: Boolean = true
)

// In tests:
val user = TestUser()
val inactiveUser = user.copy(active = false)
val differentUser = user.copy(id = TestIdGenerator.nextId(), name = "Different User")
```

---

## Async Testing

### Testing Suspending Functions

```kotlin
import kotlinx.coroutines.test.runTest

@Test
fun `publish should complete successfully`() = runTest {
    // Given
    val eventClient = EventClient.builder()
        .localEnabled(true)
        .eventBus(mockEventBus)
        .build()

    val event = TestEvent("data")

    // When
    eventClient.publish(event)

    // Then
    assertEquals(1, mockEventBus.publishedEvents.size)
}
```

### Virtual Time for Delays

```kotlin
import kotlinx.coroutines.test.*

@Test
fun `scheduler should execute after delay`() = runTest {
    // Given
    var executed = false
    val scheduler = TestScheduler(this.coroutineContext)

    scheduler.scheduleAfter(1000.milliseconds) {
        executed = true
    }

    // When
    advanceTimeBy(999)
    assertFalse(executed)

    advanceTimeBy(1)

    // Then
    assertTrue(executed)
}
```

### Testing Concurrent Operations

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@Test
fun `repository should handle concurrent writes`() = runTest {
    // Given
    val repository = UserRepository(database)
    val users = UserBuilder().buildList(100)

    // When
    val results = users.map { user ->
        async { repository.save(user) }
    }.awaitAll()

    // Then
    assertEquals(100, results.size)
    assertEquals(100, repository.count())
}
```

---

## Database Testing

### Using In-Memory H2 Database

```kotlin
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

@Test
fun `save should persist entity to database`() = runTest {
    // Given
    val database = inMemoryDatabaseConfig()

    transaction(database) {
        SchemaUtils.create(UsersTable)
    }

    val repository = UserRepository(database)
    val user = UserBuilder().build()

    // When
    val saved = repository.save(user)

    // Then
    assertNotNull(saved.id)
    assertEquals(user.name, saved.name)

    // Cleanup
    transaction(database) {
        SchemaUtils.drop(UsersTable)
    }
}
```

### Using KatalystTestEnvironment

```kotlin
import com.ead.katalyst.testing.core.katalystTestEnvironment

@Test
fun `integration test with full DI container`() = runTest {
    // Given
    val environment = katalystTestEnvironment {
        database(inMemoryDatabaseConfig())
        scan("com.ead.katalyst.example")
        feature(eventSystemFeature { withBus(true) })
    }

    try {
        val service = environment.get<UserService>()
        val repository = environment.get<UserRepository>()

        // When
        val created = service.createUser("Alice", "alice@example.com")

        // Then
        assertNotNull(repository.findById(created.id))
    } finally {
        environment.close()
    }
}
```

---

## Common Patterns

### Pattern 1: Testing CRUD Operations

```kotlin
class CrudRepositoryTest {
    private lateinit var database: Database
    private lateinit var repository: UserRepository

    @BeforeEach
    fun setup() {
        database = inMemoryDatabaseConfig()
        transaction(database) {
            SchemaUtils.create(UsersTable)
        }
        repository = UserRepository(database)
    }

    @AfterEach
    fun cleanup() {
        transaction(database) {
            SchemaUtils.drop(UsersTable)
        }
    }

    @Test
    fun `save should persist entity`() = runTest {
        val user = UserBuilder().build()
        val saved = repository.save(user)
        assertNotNull(saved.id)
    }

    @Test
    fun `findById should return entity when exists`() = runTest {
        val user = repository.save(UserBuilder().build())
        val found = repository.findById(user.id!!)
        assertEquals(user, found)
    }

    @Test
    fun `findById should return null when not exists`() = runTest {
        val found = repository.findById(999L)
        assertNull(found)
    }
}
```

### Pattern 2: Testing Event Flows

```kotlin
@Test
fun `user registration should publish event`() = runTest {
    // Given
    val handlerSpy = EventHandlerSpy<UserRegisteredEvent>()
    val mockHandler = object : EventHandler<UserRegisteredEvent> {
        override suspend fun handle(event: UserRegisteredEvent) {
            handlerSpy.record(event)
        }
    }

    val eventBus = LocalEventBus()
    eventBus.subscribe(UserRegisteredEvent::class, mockHandler)

    val service = UserRegistrationService(repository, eventBus)

    // When
    val user = service.register("alice@example.com", "password")

    // Then
    handlerSpy.assertInvocationCount(1)
    assertEquals(user.id, handlerSpy.lastEvent?.userId)
}
```

### Pattern 3: Testing HTTP Endpoints

```kotlin
import com.ead.katalyst.testing.ktor.katalystTestApplication
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

@Test
fun `POST auth register should create user`() = runTest {
    katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("com.ead.katalyst.example.auth")
        }
    ) { environment ->
        // When
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "alice@example.com",
                    "password": "Sup3rSecure!",
                    "displayName": "Alice"
                }
            """.trimIndent())
        }

        // Then
        assertEquals(HttpStatusCode.Created, response.status)

        val repository = environment.get<AuthRepository>()
        val user = repository.findByEmail("alice@example.com")
        assertNotNull(user)
    }
}
```

### Pattern 4: Testing Retry Logic

```kotlin
@Test
fun `exponential backoff should retry with increasing delays`() = runTest {
    // Given
    val attempts = mutableListOf<Long>()
    var callCount = 0

    val policy = ExponentialBackoffRetry(
        baseDelay = 100.milliseconds,
        maxAttempts = 3
    )

    val operation = suspend {
        callCount++
        attempts.add(System.currentTimeMillis())
        if (callCount < 3) {
            throw RuntimeException("Simulated failure")
        }
        "success"
    }

    // When
    val result = policy.execute(operation)

    // Then
    assertEquals("success", result)
    assertEquals(3, callCount)
    assertTrue(attempts[1] - attempts[0] >= 100)
    assertTrue(attempts[2] - attempts[1] >= 200)
}
```

---

## Quick Reference

### Test File Organization

```
module/src/test/kotlin/com/ead/katalyst/module/
├── repository/
│   ├── UserRepositoryTest.kt
│   └── OrderRepositoryTest.kt
├── service/
│   ├── AuthenticationServiceTest.kt
│   └── OrderServiceTest.kt
├── integration/
│   └── UserRegistrationIntegrationTest.kt
└── e2e/
    └── AuthApiE2ETest.kt
```

### Test Annotations

```kotlin
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

class MyTest {
    @BeforeTest
    fun setup() {
        // Runs before each test
    }

    @Test
    fun `test name`() {
        // Test body
    }

    @AfterTest
    fun cleanup() {
        // Runs after each test
    }
}
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :katalyst-persistence:test

# Run single test class
./gradlew :katalyst-persistence:test --tests "CrudRepositoryTest"

# Run single test method
./gradlew :katalyst-persistence:test --tests "CrudRepositoryTest.save should persist entity"

# Generate coverage report
./gradlew koverHtmlReport

# Run tests in parallel
./gradlew test --parallel --max-workers=4
```

---

## Summary

1. **Use descriptive test names** with backtick syntax
2. **Follow Given-When-Then** structure
3. **Use inline anonymous objects** instead of mocking libraries
4. **Leverage test utilities** for common operations
5. **Keep tests focused** - one logical assertion per test
6. **Use builders** for complex test data
7. **Clean up resources** in @AfterTest or try-finally blocks
8. **Test async code** with runTest and virtual time
9. **Use in-memory databases** for fast, isolated tests
10. **Document complex test scenarios** with comments

---

**Happy Testing! 🎉**
