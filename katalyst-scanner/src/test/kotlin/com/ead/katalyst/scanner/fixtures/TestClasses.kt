package com.ead.katalyst.scanner.fixtures

import com.ead.katalyst.validators.ValidationResult
import com.ead.katalyst.validators.Validator

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
