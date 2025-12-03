package io.github.darkryh.katalyst.scanner.fixtures

import io.github.darkryh.katalyst.core.validation.ValidationResult
import io.github.darkryh.katalyst.core.validation.Validator
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.EventMetadata

// ============= Test-Only Domain Models =============

/**
 * Test user model for scanner tests.
 */
data class User(val id: Long, val name: String)

/**
 * Test user DTO for scanner tests.
 */
data class UserDTO(val id: Long, val name: String)

// ============= Base Test Service Interface (Scanner Testing Only) =============

interface TestService

// ============= Generic Type Test Helpers (Scanner Testing Only) =============

interface SingleTypeGeneric<T>

class StringHolder : SingleTypeGeneric<String>
class IntHolder : SingleTypeGeneric<Int>

// ============= Method Discovery Test Services =============

class ServiceWithAnnotatedMethods : TestService {
    suspend fun handleRequest(id: Long): String {
        return "result"
    }

    fun processData(data: String): Int {
        return data.length
    }

    fun unannotatedMethod() {
        // No annotation
    }
}

class ServiceWithSuspendMethods : TestService {
    suspend fun suspendMethod1(): String = "result1"
    suspend fun suspendMethod2(param: Int): String = "result2"
    fun normalMethod(): String = "normal"
}

class ServiceWithMultipleParameters : TestService {
    suspend fun multiParam(a: String, b: Int, c: Double): String = "result"
    suspend fun singleParam(x: Long): String = "x"
    fun noParams(): String = "result"
}

class EmptyService : TestService {
    // No methods to discover
}

// ============= Predicate Test Classes =============

class ConcreteTestClass : TestService
abstract class AbstractTestClass : TestService
interface InterfaceTestClass : TestService

class TestClassWithNoArgsConstructor : TestService

class TestClassWithArgsConstructor(val dependency: String) : TestService

@Suppress("UNUSED")
class TestClassWithMethods : TestService {
    fun publicMethod() {}
    private fun privateMethod() {}
}

// ============= Optional Parameters Test =============

class ServiceWithOptionalParams : TestService {
    suspend fun methodWithOptional(required: String, optional: Int = 10): String = required

    suspend fun allOptional(a: String = "default", b: Int = 0): String = a
}

// ============= Generic Repository Test Fixtures =============

/**
 * Base generic repository interface for type parameter extraction tests.
 */
interface SampleRepository<E, D>

/**
 * Concrete user repository implementation for testing generic type extraction.
 */
class UserRepository : SampleRepository<User, UserDTO>

/**
 * Concrete product repository implementation for testing generic type extraction.
 */
class ProductRepository : SampleRepository<Product, ProductDTO>

/**
 * Base repository type that other repositories can extend.
 */
open class BaseRepository<E, D> : SampleRepository<E, D>

/**
 * Repository implementation with an inheritance layer to test generic extraction across hierarchies.
 */
class ConcreteRepository : BaseRepository<User, UserDTO>()

/**
 * Repository that implements additional interfaces to simulate complex hierarchies.
 */
interface AuditableRepository

class NestedRepository : BaseRepository<User, UserDTO>(), AuditableRepository

/**
 * Plain entity used for negative generic tests.
 */
class PlainEntity

/**
 * Sample event and handler for generic extraction tests.
 */
data class UserCreatedEvent(
    val id: Long,
    private val metadata: EventMetadata = EventMetadata(eventType = "user.created")
) : DomainEvent {
    override fun getMetadata(): EventMetadata = metadata
}

class UserCreatedEventHandler : EventHandler<UserCreatedEvent> {
    override val eventType = UserCreatedEvent::class

    override suspend fun handle(event: UserCreatedEvent) {
        // no-op for tests
    }
}

// ============= Real-World Annotated Methods Test =============

// ============= Product Model (for Repository tests) =============

/**
 * Test product model for scanner tests.
 */
data class Product(val id: Long, val price: Double)

/**
 * Test product DTO for scanner tests.
 */
data class ProductDTO(val id: Long, val price: Double)

// ============= Validator Test Helpers =============

/**
 * Test validator for User entities.
 */
class UserValidator : Validator<User> {
    override suspend fun validate(entity: User): ValidationResult {
        return if (entity.name.isNotEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid("User name cannot be empty")
        }
    }
}

/**
 * Test validator for Product entities.
 */
class ProductValidator : Validator<Product> {
    override suspend fun validate(entity: Product): ValidationResult {
        return if (entity.price > 0) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid("Product price must be positive")
        }
    }
}
