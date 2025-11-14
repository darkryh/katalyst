package com.ead.katalyst.testing.core

import kotlin.test.*

/**
 * Validation tests for test utilities.
 * These tests ensure our testing infrastructure works correctly.
 */
class TestHelpersValidationTest {

    @Test
    fun `assertThrowsWithMessage should catch exception with expected message`() {
        // When/Then
        val exception = assertThrowsWithMessage<IllegalArgumentException>("invalid") {
            throw IllegalArgumentException("This is invalid")
        }

        assertEquals("This is invalid", exception.message)
    }

    @Test
    fun `assertThrowsWithMessage should fail when no exception thrown`() {
        // When/Then
        val failure = assertFailsWith<AssertionError> {
            assertThrowsWithMessage<IllegalArgumentException> {
                // No exception thrown
            }
        }

        assertTrue(failure.message?.contains("Expected") == true)
    }

    @Test
    fun `uniqueTestId should generate unique identifiers`() {
        // When
        val id1 = uniqueTestId()
        val id2 = uniqueTestId()

        // Then
        assertNotEquals(id1, id2)
        assertTrue(id1.startsWith("test-"))
        assertTrue(id2.startsWith("test-"))
    }

    @Test
    fun `uniqueEmail should generate unique emails`() {
        // When
        val email1 = uniqueEmail()
        val email2 = uniqueEmail()

        // Then
        assertNotEquals(email1, email2)
        assertTrue(email1.contains("@example.com"))
        assertTrue(email2.contains("@example.com"))
    }

    @Test
    fun `assertContainsExactly should pass when collections match`() {
        // Given
        val actual = listOf(1, 2, 3)

        // When/Then
        assertContainsExactly(actual, 1, 2, 3)
    }

    @Test
    fun `assertContainsExactly should fail when collections differ`() {
        // Given
        val actual = listOf(1, 2, 3)

        // When/Then
        assertFailsWith<AssertionError> {
            assertContainsExactly(actual, 1, 2, 4)
        }
    }

    @Test
    fun `assertContainsAll should pass when actual contains all expected`() {
        // Given
        val actual = listOf(1, 2, 3, 4, 5)

        // When/Then
        assertContainsAll(actual, 1, 3, 5)
    }

    @Test
    fun `assertContainsAll should fail when actual missing items`() {
        // Given
        val actual = listOf(1, 2, 3)

        // When/Then
        assertFailsWith<AssertionError> {
            assertContainsAll(actual, 1, 2, 3, 4)
        }
    }
}
