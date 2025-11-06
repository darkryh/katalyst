package com.ead.katalyst.scanner.fixtures

import com.ead.katalyst.repositories.Repository
import com.ead.katalyst.validators.Validator
import com.ead.katalyst.validators.ValidationResult
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.UserCreatedEvent
import com.ead.katalyst.events.UserCreatedEventHandler
import com.ead.katalyst.events.OrderPlacedEvent
import com.ead.katalyst.events.OrderPlacedEventHandler

/**
 * Test fixtures and annotations for scanner tests.
 *
 * This file contains test-specific annotations and helper classes used only
 * for testing the scanner framework.
 */

// ============= Test-Only Annotations =============

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresAuth

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(val requestsPerMinute: Int = 100)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeprecatedAnnotation(val version: String = "1.0")

// ============= Test Annotations (Scanner Testing Only) =============

annotation class TestAnnotation
annotation class AnotherAnnotation

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
    @TestAnnotation
    suspend fun handleRequest(id: Long): String {
        return "result"
    }

    @AnotherAnnotation
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

class ClassWithAnnotatedMethods : TestService {
    @TestAnnotation
    fun annotatedMethod() {}

    fun unannotatedMethod() {}
}

// ============= Optional Parameters Test =============

class ServiceWithOptionalParams : TestService {
    suspend fun methodWithOptional(required: String, optional: Int = 10): String = required

    suspend fun allOptional(a: String = "default", b: Int = 0): String = a
}

// ============= Real-World Annotated Methods Test =============

class AnnotatedMethods : TestService {
    @RequiresAuth
    suspend fun protectedMethod(): String = "protected"

    @RateLimit(50)
    suspend fun rateLimitedMethod(): String = "limited"

    @DeprecatedAnnotation("1.0")
    fun oldMethod(): String = "old"

    suspend fun normalMethod(): String = "normal"
}

// ============= Product Model (for Repository tests) =============

/**
 * Test product model for scanner tests.
 */
data class Product(val id: Long, val price: Double)

/**
 * Test product DTO for scanner tests.
 */
data class ProductDTO(val id: Long, val price: Double)

// ============= Repository Test Helpers =============

/**
 * Test repository for User entities.
 */
class UserRepository : Repository<User, UserDTO> {
    override suspend fun save(entity: User): UserDTO = UserDTO(entity.id, entity.name)
    override suspend fun findById(id: Long): User? = null
    override suspend fun findAll(): List<User> = emptyList()
    override suspend fun findAll(filter: com.ead.katalyst.repositories.QueryFilter): Pair<List<User>, com.ead.katalyst.repositories.PageInfo> {
        return Pair(emptyList(), com.ead.katalyst.repositories.PageInfo(filter.limit, filter.offset, 0))
    }
    override suspend fun count(): Long = 0
    override suspend fun delete(id: Long) {}
}

/**
 * Test repository for Product entities.
 */
class ProductRepository : Repository<Product, ProductDTO> {
    override suspend fun save(entity: Product): ProductDTO = ProductDTO(entity.id, entity.price)
    override suspend fun findById(id: Long): Product? = null
    override suspend fun findAll(): List<Product> = emptyList()
    override suspend fun findAll(filter: com.ead.katalyst.repositories.QueryFilter): Pair<List<Product>, com.ead.katalyst.repositories.PageInfo> {
        return Pair(emptyList(), com.ead.katalyst.repositories.PageInfo(filter.limit, filter.offset, 0))
    }
    override suspend fun count(): Long = 0
    override suspend fun delete(id: Long) {}
}

// ============= Complex Generic Type Test Helpers =============

/**
 * Base repository interface for advanced type extraction testing.
 */
interface BaseRepository<E : Any, D : Any>

/**
 * Specific repository interface extending Repository.
 */
interface SpecificRepository<E : Any, D : Any> : Repository<E, D>

/**
 * Concrete repository implementation with complex type hierarchy.
 */
class ConcreteRepository : SpecificRepository<User, UserDTO> {
    override suspend fun save(entity: User): UserDTO = UserDTO(entity.id, entity.name)
    override suspend fun findById(id: Long): User? = null
    override suspend fun findAll(): List<User> = emptyList()
    override suspend fun findAll(filter: com.ead.katalyst.repositories.QueryFilter): Pair<List<User>, com.ead.katalyst.repositories.PageInfo> {
        return Pair(emptyList(), com.ead.katalyst.repositories.PageInfo(filter.limit, filter.offset, 0))
    }
    override suspend fun count(): Long = 0
    override suspend fun delete(id: Long) {}
}

/**
 * Nested generic interface for advanced type extraction testing.
 */
interface NestedGeneric<T : Any, U : Any>

/**
 * Nested repository implementing both generic and repository interfaces.
 */
class NestedRepository : NestedGeneric<User, UserDTO>, Repository<User, UserDTO> {
    override suspend fun save(entity: User): UserDTO = UserDTO(entity.id, entity.name)
    override suspend fun findById(id: Long): User? = null
    override suspend fun findAll(): List<User> = emptyList()
    override suspend fun findAll(filter: com.ead.katalyst.repositories.QueryFilter): Pair<List<User>, com.ead.katalyst.repositories.PageInfo> {
        return Pair(emptyList(), com.ead.katalyst.repositories.PageInfo(filter.limit, filter.offset, 0))
    }
    override suspend fun count(): Long = 0
    override suspend fun delete(id: Long) {}
}

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
