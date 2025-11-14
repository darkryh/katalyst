package com.ead.katalyst.core.validation

import com.ead.katalyst.core.component.Component
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for Validator interface.
 *
 * Tests cover:
 * - Interface contract and default methods
 * - Custom validator implementations
 * - validate() method behavior
 * - getValidationErrors() convenience method
 * - Component marker interface integration
 * - Generic type safety
 * - Edge cases and error scenarios
 * - Practical usage scenarios
 */
class ValidatorTest {

    // ========== TEST DATA CLASSES ==========

    data class User(
        val id: String,
        val name: String,
        val email: String,
        val age: Int
    )

    data class Order(
        val orderId: String,
        val amount: Double,
        val items: List<String>
    )

    data class Product(
        val sku: String,
        val name: String,
        val price: Double
    )

    // ========== BASIC VALIDATOR IMPLEMENTATIONS ==========

    class UserValidator : Validator<User> {
        override suspend fun validate(entity: User): ValidationResult {
            val errors = mutableListOf<String>()

            if (entity.name.isEmpty()) {
                errors.add("User name cannot be empty")
            }

            if (entity.email.isEmpty() || !entity.email.contains("@")) {
                errors.add("Email must be valid format")
            }

            if (entity.age < 0 || entity.age > 150) {
                errors.add("Age must be between 0 and 150")
            }

            return if (errors.isEmpty()) {
                ValidationResult.valid()
            } else {
                ValidationResult.invalid(*errors.toTypedArray())
            }
        }
    }

    class OrderValidator : Validator<Order> {
        override suspend fun validate(entity: Order): ValidationResult {
            val errors = mutableListOf<String>()

            if (entity.orderId.isEmpty()) {
                errors.add("Order ID is required")
            }

            if (entity.amount <= 0) {
                errors.add("Amount must be greater than 0")
            }

            if (entity.items.isEmpty()) {
                errors.add("Order must contain at least one item")
            }

            return if (errors.isEmpty()) {
                ValidationResult.valid()
            } else {
                ValidationResult.invalid(*errors.toTypedArray())
            }
        }
    }

    class AlwaysValidValidator<T : Any> : Validator<T> {
        override suspend fun validate(entity: T): ValidationResult {
            return ValidationResult.valid()
        }
    }

    class AlwaysInvalidValidator<T : Any>(private val errorMessage: String) : Validator<T> {
        override suspend fun validate(entity: T): ValidationResult {
            return ValidationResult.invalid(errorMessage)
        }
    }

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `Validator should extend Component interface`() {
        // Given
        val validator = UserValidator()

        // Then
        assertTrue(validator is Component)
    }

    @Test
    fun `Validator should be generic over entity type`() {
        // Given
        val userValidator: Validator<User> = UserValidator()
        val orderValidator: Validator<Order> = OrderValidator()

        // Then - Type safety enforced at compile time
        assertNotNull(userValidator)
        assertNotNull(orderValidator)
    }

    @Test
    fun `getValidationErrors should return errors from validate result`() = runTest {
        // Given
        val validator = UserValidator()
        val invalidUser = User(
            id = "user-1",
            name = "",
            email = "invalid",
            age = 200
        )

        // When
        val errors = validator.getValidationErrors(invalidUser)

        // Then
        assertEquals(3, errors.size)
        assertTrue(errors.contains("User name cannot be empty"))
        assertTrue(errors.contains("Email must be valid format"))
        assertTrue(errors.contains("Age must be between 0 and 150"))
    }

    @Test
    fun `getValidationErrors should return empty list for valid entity`() = runTest {
        // Given
        val validator = UserValidator()
        val validUser = User(
            id = "user-1",
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )

        // When
        val errors = validator.getValidationErrors(validUser)

        // Then
        assertTrue(errors.isEmpty())
    }

    // ========== CUSTOM VALIDATOR TESTS ==========

    @Test
    fun `UserValidator should validate valid user successfully`() = runTest {
        // Given
        val validator = UserValidator()
        val validUser = User(
            id = "user-1",
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )

        // When
        val result = validator.validate(validUser)

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `UserValidator should detect empty name`() = runTest {
        // Given
        val validator = UserValidator()
        val invalidUser = User(
            id = "user-1",
            name = "",
            email = "john@example.com",
            age = 30
        )

        // When
        val result = validator.validate(invalidUser)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.contains("User name cannot be empty"))
    }

    @Test
    fun `UserValidator should detect invalid email format`() = runTest {
        // Given
        val validator = UserValidator()
        val invalidUser = User(
            id = "user-1",
            name = "John Doe",
            email = "invalid-email",
            age = 30
        )

        // When
        val result = validator.validate(invalidUser)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Email must be valid format"))
    }

    @Test
    fun `UserValidator should detect invalid age range`() = runTest {
        // Given
        val validator = UserValidator()
        val userTooOld = User(
            id = "user-1",
            name = "John Doe",
            email = "john@example.com",
            age = 200
        )

        // When
        val result = validator.validate(userTooOld)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Age must be between 0 and 150"))
    }

    @Test
    fun `UserValidator should detect negative age`() = runTest {
        // Given
        val validator = UserValidator()
        val userNegativeAge = User(
            id = "user-1",
            name = "John Doe",
            email = "john@example.com",
            age = -1
        )

        // When
        val result = validator.validate(userNegativeAge)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Age must be between 0 and 150"))
    }

    @Test
    fun `UserValidator should accumulate multiple validation errors`() = runTest {
        // Given
        val validator = UserValidator()
        val invalidUser = User(
            id = "user-1",
            name = "",
            email = "invalid",
            age = -10
        )

        // When
        val result = validator.validate(invalidUser)

        // Then
        assertFalse(result.isValid)
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.contains("User name cannot be empty"))
        assertTrue(result.errors.contains("Email must be valid format"))
        assertTrue(result.errors.contains("Age must be between 0 and 150"))
    }

    @Test
    fun `OrderValidator should validate valid order successfully`() = runTest {
        // Given
        val validator = OrderValidator()
        val validOrder = Order(
            orderId = "order-123",
            amount = 99.99,
            items = listOf("item1", "item2")
        )

        // When
        val result = validator.validate(validOrder)

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `OrderValidator should detect empty order ID`() = runTest {
        // Given
        val validator = OrderValidator()
        val invalidOrder = Order(
            orderId = "",
            amount = 99.99,
            items = listOf("item1")
        )

        // When
        val result = validator.validate(invalidOrder)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Order ID is required"))
    }

    @Test
    fun `OrderValidator should detect zero amount`() = runTest {
        // Given
        val validator = OrderValidator()
        val invalidOrder = Order(
            orderId = "order-123",
            amount = 0.0,
            items = listOf("item1")
        )

        // When
        val result = validator.validate(invalidOrder)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Amount must be greater than 0"))
    }

    @Test
    fun `OrderValidator should detect negative amount`() = runTest {
        // Given
        val validator = OrderValidator()
        val invalidOrder = Order(
            orderId = "order-123",
            amount = -50.0,
            items = listOf("item1")
        )

        // When
        val result = validator.validate(invalidOrder)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Amount must be greater than 0"))
    }

    @Test
    fun `OrderValidator should detect empty items list`() = runTest {
        // Given
        val validator = OrderValidator()
        val invalidOrder = Order(
            orderId = "order-123",
            amount = 99.99,
            items = emptyList()
        )

        // When
        val result = validator.validate(invalidOrder)

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Order must contain at least one item"))
    }

    @Test
    fun `OrderValidator should accumulate multiple validation errors`() = runTest {
        // Given
        val validator = OrderValidator()
        val invalidOrder = Order(
            orderId = "",
            amount = -10.0,
            items = emptyList()
        )

        // When
        val result = validator.validate(invalidOrder)

        // Then
        assertFalse(result.isValid)
        assertEquals(3, result.errors.size)
    }

    // ========== GENERIC VALIDATOR TESTS ==========

    @Test
    fun `AlwaysValidValidator should always return valid result`() = runTest {
        // Given
        val validator = AlwaysValidValidator<User>()
        val user = User("id", "", "", -1)  // Even invalid data

        // When
        val result = validator.validate(user)

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `AlwaysInvalidValidator should always return invalid result`() = runTest {
        // Given
        val errorMessage = "Validation always fails"
        val validator = AlwaysInvalidValidator<User>(errorMessage)
        val user = User("id", "John", "john@test.com", 30)  // Even valid data

        // When
        val result = validator.validate(user)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals(errorMessage, result.errors.first())
    }

    @Test
    fun `AlwaysValidValidator should work with different types`() = runTest {
        // Given
        val userValidator = AlwaysValidValidator<User>()
        val orderValidator = AlwaysValidValidator<Order>()
        val productValidator = AlwaysValidValidator<Product>()

        // When
        val userResult = userValidator.validate(User("id", "", "", 0))
        val orderResult = orderValidator.validate(Order("", 0.0, emptyList()))
        val productResult = productValidator.validate(Product("", "", 0.0))

        // Then
        assertTrue(userResult.isValid)
        assertTrue(orderResult.isValid)
        assertTrue(productResult.isValid)
    }

    // ========== ASYNC VALIDATION TESTS ==========

    class AsyncValidator : Validator<User> {
        var validationCount = 0

        override suspend fun validate(entity: User): ValidationResult {
            validationCount++
            kotlinx.coroutines.delay(10)  // Simulate async work
            return if (entity.name.isNotEmpty()) {
                ValidationResult.valid()
            } else {
                ValidationResult.invalid("Name is required")
            }
        }
    }

    @Test
    fun `validator should support async validation`() = runTest {
        // Given
        val validator = AsyncValidator()
        val user = User("id", "John", "john@test.com", 30)

        // When
        val result = validator.validate(user)

        // Then
        assertTrue(result.isValid)
        assertEquals(1, validator.validationCount)
    }

    @Test
    fun `validator should support sequential async validations`() = runTest {
        // Given
        val validator = AsyncValidator()
        val user1 = User("id1", "John", "john@test.com", 30)
        val user2 = User("id2", "Jane", "jane@test.com", 25)

        // When
        val result1 = validator.validate(user1)
        val result2 = validator.validate(user2)

        // Then
        assertTrue(result1.isValid)
        assertTrue(result2.isValid)
        assertEquals(2, validator.validationCount)
    }

    // ========== VALIDATOR COMPOSITION TESTS ==========

    class CompositeValidator<T : Any>(
        private val validators: List<Validator<T>>
    ) : Validator<T> {
        override suspend fun validate(entity: T): ValidationResult {
            val allErrors = mutableListOf<String>()

            for (validator in validators) {
                val result = validator.validate(entity)
                if (!result.isValid) {
                    allErrors.addAll(result.errors)
                }
            }

            return if (allErrors.isEmpty()) {
                ValidationResult.valid()
            } else {
                ValidationResult.invalid(*allErrors.toTypedArray())
            }
        }
    }

    class NameValidator : Validator<User> {
        override suspend fun validate(entity: User): ValidationResult {
            return if (entity.name.isEmpty()) {
                ValidationResult.invalid("Name is required")
            } else {
                ValidationResult.valid()
            }
        }
    }

    class EmailValidator : Validator<User> {
        override suspend fun validate(entity: User): ValidationResult {
            return if (!entity.email.contains("@")) {
                ValidationResult.invalid("Email must contain @")
            } else {
                ValidationResult.valid()
            }
        }
    }

    @Test
    fun `CompositeValidator should combine multiple validators`() = runTest {
        // Given
        val nameValidator = NameValidator()
        val emailValidator = EmailValidator()
        val compositeValidator = CompositeValidator(listOf(nameValidator, emailValidator))
        val invalidUser = User("id", "", "invalid", 30)

        // When
        val result = compositeValidator.validate(invalidUser)

        // Then
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.contains("Name is required"))
        assertTrue(result.errors.contains("Email must contain @"))
    }

    @Test
    fun `CompositeValidator should pass when all validators pass`() = runTest {
        // Given
        val nameValidator = NameValidator()
        val emailValidator = EmailValidator()
        val compositeValidator = CompositeValidator(listOf(nameValidator, emailValidator))
        val validUser = User("id", "John", "john@test.com", 30)

        // When
        val result = compositeValidator.validate(validUser)

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `CompositeValidator should work with empty validator list`() = runTest {
        // Given
        val compositeValidator = CompositeValidator<User>(emptyList())
        val user = User("id", "", "", 0)

        // When
        val result = compositeValidator.validate(user)

        // Then
        assertTrue(result.isValid)
    }

    // ========== EDGE CASE TESTS ==========

    class ExceptionThrowingValidator : Validator<User> {
        override suspend fun validate(entity: User): ValidationResult {
            throw IllegalStateException("Validation failed catastrophically")
        }
    }

    @Test
    fun `validator can throw exceptions during validation`() = runTest {
        // Given
        val validator = ExceptionThrowingValidator()
        val user = User("id", "John", "john@test.com", 30)

        // When/Then
        assertFailsWith<IllegalStateException> {
            validator.validate(user)
        }
    }

    class CustomErrorValidator : Validator<User> {
        override suspend fun validate(entity: User): ValidationResult {
            val errors = buildList {
                add("Error 1")
                add("Error 2")
                if (entity.name.isEmpty()) {
                    add("Error 3")
                }
            }
            return ValidationResult.invalid(*errors.toTypedArray())
        }
    }

    @Test
    fun `validator should support custom error accumulation`() = runTest {
        // Given
        val validator = CustomErrorValidator()
        val user = User("id", "", "john@test.com", 30)

        // When
        val result = validator.validate(user)

        // Then
        assertFalse(result.isValid)
        assertEquals(3, result.errors.size)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical validation workflow for user registration`() = runTest {
        // Given
        val validator = UserValidator()
        val newUser = User(
            id = "user-123",
            name = "John Doe",
            email = "john.doe@example.com",
            age = 25
        )

        // When
        val result = validator.validate(newUser)

        // Then - Validation passes, user can be registered
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `typical validation workflow for invalid user registration`() = runTest {
        // Given
        val validator = UserValidator()
        val newUser = User(
            id = "user-123",
            name = "",
            email = "invalid-email",
            age = 16
        )

        // When
        val result = validator.validate(newUser)

        // Then - Validation fails, collect errors for user feedback
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 2)
        val errorMessages = result.errors.joinToString(", ")
        assertTrue(errorMessages.contains("name"))
        assertTrue(errorMessages.contains("Email"))
    }

    @Test
    fun `validation with early return pattern`() = runTest {
        // Given
        val validator = UserValidator()
        val user = User("id", "", "", -1)

        // When
        val result = validator.validate(user)
        if (!result.isValid) {
            // Early return with errors
            val errors = result.errors
            assertFalse(errors.isEmpty())
            return@runTest
        }

        // Then - This should not execute
        fail("Should have returned early due to validation errors")
    }

    @Test
    fun `getValidationErrors convenience method for form validation`() = runTest {
        // Given
        val validator = UserValidator()
        val formData = User("id", "", "no-at-sign", 200)

        // When - Get errors directly for display
        val errors = validator.getValidationErrors(formData)

        // Then
        assertEquals(3, errors.size)
        // Can be used directly to populate form error messages
        assertTrue(errors.any { it.contains("name") })
        assertTrue(errors.any { it.contains("Email") })
        assertTrue(errors.any { it.contains("Age") })
    }
}
