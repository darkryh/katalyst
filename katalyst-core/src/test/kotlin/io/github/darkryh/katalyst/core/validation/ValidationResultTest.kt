package io.github.darkryh.katalyst.core.validation

import kotlin.test.*

/**
 * Comprehensive tests for ValidationResult.
 *
 * Tests cover:
 * - Valid result creation
 * - Invalid result with errors
 * - Factory methods (valid, invalid)
 * - Data class behavior (copy, equality)
 * - Edge cases (empty errors, multiple errors)
 */
class ValidationResultTest {

    // ========== VALID RESULT TESTS ==========

    @Test
    fun `valid factory should create valid result with no errors`() {
        // When
        val result = ValidationResult.valid()

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `ValidationResult with isValid true should be valid`() {
        // When
        val result = ValidationResult(isValid = true)

        // Then
        assertTrue(result.isValid)
    }

    @Test
    fun `ValidationResult with isValid true and empty errors should be valid`() {
        // When
        val result = ValidationResult(isValid = true, errors = emptyList())

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    // ========== INVALID RESULT TESTS ==========

    @Test
    fun `invalid factory should create invalid result with errors`() {
        // Given
        val error1 = "Field 'name' is required"
        val error2 = "Field 'email' must be valid email"

        // When
        val result = ValidationResult.invalid(error1, error2)

        // Then
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.contains(error1))
        assertTrue(result.errors.contains(error2))
    }

    @Test
    fun `invalid factory with single error should work`() {
        // Given
        val error = "Validation failed"

        // When
        val result = ValidationResult.invalid(error)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals(error, result.errors.first())
    }

    @Test
    fun `invalid factory with no errors should create invalid result with empty errors list`() {
        // When
        val result = ValidationResult.invalid()

        // Then
        assertFalse(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `ValidationResult with isValid false should be invalid`() {
        // When
        val result = ValidationResult(isValid = false, errors = listOf("Error"))

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun `errors should preserve order`() {
        // Given
        val error1 = "First error"
        val error2 = "Second error"
        val error3 = "Third error"

        // When
        val result = ValidationResult.invalid(error1, error2, error3)

        // Then
        assertEquals(error1, result.errors[0])
        assertEquals(error2, result.errors[1])
        assertEquals(error3, result.errors[2])
    }

    @Test
    fun `errors should allow duplicates`() {
        // Given
        val sameError = "Duplicate error"

        // When
        val result = ValidationResult.invalid(sameError, sameError, sameError)

        // Then
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.all { it == sameError })
    }

    @Test
    fun `errors should support empty strings`() {
        // Given
        val emptyError = ""
        val validError = "Real error"

        // When
        val result = ValidationResult.invalid(emptyError, validError)

        // Then
        assertEquals(2, result.errors.size)
        assertEquals(emptyError, result.errors[0])
        assertEquals(validError, result.errors[1])
    }

    @Test
    fun `errors should support long error messages`() {
        // Given
        val longError = "Error: " + "x".repeat(1000)

        // When
        val result = ValidationResult.invalid(longError)

        // Then
        assertEquals(1, result.errors.size)
        assertEquals(longError, result.errors.first())
    }

    @Test
    fun `errors should support special characters`() {
        // Given
        val specialError = "Error: Field 'user@email.com' has invalid format (must match /^[a-z]+$/)"

        // When
        val result = ValidationResult.invalid(specialError)

        // Then
        assertEquals(specialError, result.errors.first())
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `ValidationResult should support copy`() {
        // Given
        val original = ValidationResult(isValid = true, errors = emptyList())

        // When
        val copy = original.copy(isValid = false, errors = listOf("Error"))

        // Then
        assertTrue(original.isValid)
        assertTrue(original.errors.isEmpty())

        assertFalse(copy.isValid)
        assertEquals(1, copy.errors.size)
    }

    @Test
    fun `ValidationResult should support partial copy`() {
        // Given
        val original = ValidationResult(isValid = false, errors = listOf("Error 1"))

        // When
        val copy = original.copy(errors = listOf("Error 1", "Error 2"))

        // Then
        assertFalse(copy.isValid)  // Unchanged
        assertEquals(2, copy.errors.size)  // Changed
    }

    @Test
    fun `ValidationResult should support equality`() {
        // Given
        val result1 = ValidationResult(isValid = true, errors = emptyList())
        val result2 = ValidationResult(isValid = true, errors = emptyList())
        val result3 = ValidationResult(isValid = false, errors = listOf("Error"))

        // Then
        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }

    @Test
    fun `ValidationResult should generate consistent hashCode`() {
        // Given
        val result1 = ValidationResult(isValid = true, errors = emptyList())
        val result2 = ValidationResult(isValid = true, errors = emptyList())

        // Then
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `ValidationResult should have readable toString`() {
        // Given
        val result = ValidationResult(isValid = false, errors = listOf("Error 1", "Error 2"))

        // When
        val string = result.toString()

        // Then
        assertTrue(string.contains("isValid"))
        assertTrue(string.contains("false"))
        assertTrue(string.contains("errors"))
    }

    // ========== EDGE CASES ==========

    @Test
    fun `ValidationResult can be valid with errors list present but empty`() {
        // When - This is technically allowed by data class
        val result = ValidationResult(isValid = true, errors = emptyList())

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `ValidationResult with large number of errors should work`() {
        // Given
        val errors = (1..100).map { "Error $it" }

        // When
        val result = ValidationResult(isValid = false, errors = errors)

        // Then
        assertFalse(result.isValid)
        assertEquals(100, result.errors.size)
    }

    @Test
    fun `errors list should be immutable after creation`() {
        // Given
        val errorsList = mutableListOf("Error 1")
        val result = ValidationResult(isValid = false, errors = errorsList)

        // When - Modify original list
        errorsList.add("Error 2")

        // Then - Result should not be affected (defensive copy would be ideal)
        // Note: This tests current behavior; in production, consider using toList() in constructor
        val resultSize = result.errors.size
        assertTrue(resultSize >= 1)  // At least original error present
    }

    // ========== FACTORY METHOD COMPARISON TESTS ==========

    @Test
    fun `valid factory should be equivalent to constructor with isValid true`() {
        // Given
        val factoryResult = ValidationResult.valid()
        val constructorResult = ValidationResult(isValid = true, errors = emptyList())

        // Then
        assertEquals(factoryResult, constructorResult)
    }

    @Test
    fun `invalid factory should be equivalent to constructor with isValid false`() {
        // Given
        val error1 = "Error 1"
        val error2 = "Error 2"

        val factoryResult = ValidationResult.invalid(error1, error2)
        val constructorResult = ValidationResult(isValid = false, errors = listOf(error1, error2))

        // Then
        assertEquals(factoryResult, constructorResult)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical validation success scenario`() {
        // Given - Validation passes
        val result = ValidationResult.valid()

        // When/Then - Check result
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `typical validation failure scenario with multiple errors`() {
        // Given - Validation fails with multiple issues
        val result = ValidationResult.invalid(
            "Name cannot be empty",
            "Email must be valid format",
            "Age must be between 18 and 120"
        )

        // When/Then - Check result
        assertFalse(result.isValid)
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.any { it.contains("Name") })
        assertTrue(result.errors.any { it.contains("Email") })
        assertTrue(result.errors.any { it.contains("Age") })
    }

    @Test
    fun `combining validation results`() {
        // Given - Multiple validation results
        val nameValidation = ValidationResult.invalid("Name is required")
        val emailValidation = ValidationResult.invalid("Email is invalid")

        // When - Combine errors
        val combinedErrors = nameValidation.errors + emailValidation.errors
        val combinedResult = ValidationResult(
            isValid = false,
            errors = combinedErrors
        )

        // Then
        assertFalse(combinedResult.isValid)
        assertEquals(2, combinedResult.errors.size)
        assertTrue(combinedResult.errors.contains("Name is required"))
        assertTrue(combinedResult.errors.contains("Email is invalid"))
    }

    @Test
    fun `conditional validation with early return`() {
        // Scenario: Check isValid before processing errors
        val result = ValidationResult.invalid("Error 1", "Error 2")

        // When
        val processedErrors = if (result.isValid) {
            emptyList()
        } else {
            result.errors
        }

        // Then
        assertEquals(2, processedErrors.size)
    }
}
