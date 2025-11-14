package com.ead.katalyst.testing.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Test data builders and factories for creating test objects.
 *
 * This file provides reusable patterns for creating test data with sensible defaults.
 */

/**
 * Auto-incrementing ID generator for test entities.
 * Thread-safe for parallel test execution.
 */
object TestIdGenerator {
    private val counter = AtomicLong(1)

    /**
     * Generates the next unique ID.
     */
    fun nextId(): Long = counter.getAndIncrement()

    /**
     * Resets the counter. Use sparingly - only when test isolation requires it.
     */
    fun reset() {
        counter.set(1)
    }
}

/**
 * Base interface for test data builders.
 *
 * Provides a consistent pattern for building test objects with fluent API.
 *
 * Example usage:
 * ```kotlin
 * data class User(val id: Long, val name: String, val email: String)
 *
 * class UserBuilder : TestDataBuilder<User> {
 *     private var id: Long = TestIdGenerator.nextId()
 *     private var name: String = "Test User"
 *     private var email: String = uniqueEmail()
 *
 *     fun withId(id: Long) = apply { this.id = id }
 *     fun withName(name: String) = apply { this.name = name }
 *     fun withEmail(email: String) = apply { this.email = email }
 *
 *     override fun build(): User = User(id, name, email)
 * }
 *
 * // In tests:
 * val user = UserBuilder()
 *     .withName("Alice")
 *     .build()
 * ```
 */
interface TestDataBuilder<out T> {
    /**
     * Builds the test object with current configuration.
     */
    fun build(): T
}

/**
 * Extension function to quickly build multiple instances.
 */
fun <T> TestDataBuilder<T>.buildList(count: Int): List<T> =
    (1..count).map { build() }

/**
 * Creates a test timestamp in milliseconds.
 * Uses a fixed epoch for deterministic tests unless overridden.
 */
object TestTimestamps {
    private const val BASE_TIMESTAMP = 1609459200000L // 2021-01-01 00:00:00 UTC

    /**
     * Returns a deterministic timestamp for test purposes.
     * Can be offset from the base for creating sequences.
     */
    fun at(offsetMillis: Long = 0): Long = BASE_TIMESTAMP + offsetMillis

    /**
     * Returns the current actual timestamp (for integration tests).
     */
    fun now(): Long = System.currentTimeMillis()
}

/**
 * Factory for creating in-memory fake repositories.
 *
 * Example usage:
 * ```kotlin
 * interface UserRepository {
 *     fun save(user: User): User
 *     fun findById(id: Long): User?
 * }
 *
 * class FakeUserRepository : UserRepository {
 *     private val storage = InMemoryStorage<Long, User>()
 *
 *     override fun save(user: User): User {
 *         storage.save(user.id, user)
 *         return user
 *     }
 *
 *     override fun findById(id: Long): User? =
 *         storage.findById(id)
 * }
 * ```
 */
class InMemoryStorage<ID, ENTITY> {
    private val data = mutableMapOf<ID, ENTITY>()
    private val lock = Any()

    /**
     * Saves an entity.
     */
    fun save(id: ID, entity: ENTITY): ENTITY = synchronized(lock) {
        data[id] = entity
        entity
    }

    /**
     * Finds an entity by ID.
     */
    fun findById(id: ID): ENTITY? = synchronized(lock) {
        data[id]
    }

    /**
     * Finds all entities.
     */
    fun findAll(): List<ENTITY> = synchronized(lock) {
        data.values.toList()
    }

    /**
     * Deletes an entity by ID.
     */
    fun delete(id: ID): Boolean = synchronized(lock) {
        data.remove(id) != null
    }

    /**
     * Deletes all entities.
     */
    fun deleteAll() = synchronized(lock) {
        data.clear()
    }

    /**
     * Counts entities.
     */
    fun count(): Int = synchronized(lock) {
        data.size
    }

    /**
     * Checks if ID exists.
     */
    fun exists(id: ID): Boolean = synchronized(lock) {
        data.containsKey(id)
    }
}

/**
 * Spy for tracking event handler invocations.
 *
 * Example usage:
 * ```kotlin
 * val handlerSpy = EventHandlerSpy<UserRegisteredEvent>()
 *
 * eventBus.subscribe(UserRegisteredEvent::class, handlerSpy)
 * eventBus.publish(UserRegisteredEvent(...))
 *
 * assertEquals(1, handlerSpy.invocationCount)
 * assertEquals(expectedEvent, handlerSpy.lastEvent)
 * ```
 */
class EventHandlerSpy<T : Any> {
    private val _events = mutableListOf<T>()
    private val lock = Any()

    /**
     * Records an event invocation.
     */
    fun record(event: T) = synchronized(lock) {
        _events.add(event)
    }

    /**
     * All captured events.
     */
    val events: List<T>
        get() = synchronized(lock) { _events.toList() }

    /**
     * Number of times handler was invoked.
     */
    val invocationCount: Int
        get() = synchronized(lock) { _events.size }

    /**
     * Last event received, or null if none.
     */
    val lastEvent: T?
        get() = synchronized(lock) { _events.lastOrNull() }

    /**
     * First event received, or null if none.
     */
    val firstEvent: T?
        get() = synchronized(lock) { _events.firstOrNull() }

    /**
     * Clears all recorded events.
     */
    fun clear() = synchronized(lock) {
        _events.clear()
    }

    /**
     * Asserts that exactly [count] events were received.
     */
    fun assertInvocationCount(count: Int) {
        val actual = invocationCount
        if (actual != count) {
            throw AssertionError("Expected $count invocations, but was $actual")
        }
    }

    /**
     * Asserts that at least one event was received.
     */
    fun assertInvoked() {
        if (invocationCount == 0) {
            throw AssertionError("Expected at least one invocation, but handler was never called")
        }
    }

    /**
     * Asserts that no events were received.
     */
    fun assertNotInvoked() {
        if (invocationCount > 0) {
            throw AssertionError("Expected no invocations, but handler was called $invocationCount times")
        }
    }
}
