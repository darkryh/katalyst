package com.ead.katalyst.testing.core

import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Common test helper functions for Katalyst testing.
 *
 * This file provides reusable utilities for:
 * - Assertions with better error messages
 * - Test data generation
 * - Common test patterns
 */

/**
 * Asserts that a block throws an exception of type [T] with an optional message check.
 *
 * @param expectedMessage Optional expected substring in the exception message
 * @param block The code block expected to throw
 * @return The thrown exception for further assertions
 */
inline fun <reified T : Throwable> assertThrowsWithMessage(
    expectedMessage: String? = null,
    block: () -> Unit
): T {
    try {
        block()
        fail("Expected ${T::class.simpleName} to be thrown, but nothing was thrown")
    } catch (e: Throwable) {
        when (e) {
            is T -> {
                if (expectedMessage != null) {
                    assertTrue(
                        e.message?.contains(expectedMessage) == true,
                        "Expected exception message to contain '$expectedMessage', but was: ${e.message}"
                    )
                }
                return e
            }
            else -> throw e
        }
    }
}

/**
 * Generates a unique test identifier using current timestamp.
 * Useful for creating unique test data.
 */
private val uniqueCounter = AtomicLong(System.nanoTime())

fun uniqueTestId(): String = "test-${uniqueCounter.incrementAndGet()}"

/**
 * Generates a unique email for testing.
 */
fun uniqueEmail(): String = "${uniqueTestId()}@example.com"

/**
 * Retries a suspending block up to [maxAttempts] times with a delay between attempts.
 * Useful for testing eventual consistency scenarios.
 *
 * @param maxAttempts Maximum number of retry attempts (default: 3)
 * @param delayMillis Delay between attempts in milliseconds (default: 100ms)
 * @param block The suspending block to retry
 * @return The result of the successful execution
 * @throws The last exception if all attempts fail
 */
suspend fun <T> retryWithDelay(
    maxAttempts: Int = 3,
    delayMillis: Long = 100,
    block: suspend () -> T
): T {
    var lastException: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e
            if (attempt < maxAttempts - 1) {
                kotlinx.coroutines.delay(delayMillis)
            }
        }
    }
    throw lastException ?: IllegalStateException("Retry failed without exception")
}

/**
 * Asserts that a collection contains exactly the expected items (order independent).
 */
fun <T> assertContainsExactly(actual: Collection<T>, vararg expected: T) {
    val expectedSet = expected.toSet()
    val actualSet = actual.toSet()

    assertTrue(
        actualSet == expectedSet,
        "Expected collection to contain exactly $expectedSet, but was $actualSet"
    )
}

/**
 * Asserts that a collection contains all expected items (may contain others).
 */
fun <T> assertContainsAll(actual: Collection<T>, vararg expected: T) {
    val missingItems = expected.toSet() - actual.toSet()
    assertTrue(
        missingItems.isEmpty(),
        "Expected collection to contain all of ${expected.toList()}, but missing: $missingItems"
    )
}
