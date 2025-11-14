package com.ead.katalyst.testing.core

import kotlin.test.*

/**
 * Validation tests for test data builders.
 */
class TestDataBuildersValidationTest {

    // Test entity for validation
    data class TestUser(
        val id: Long,
        val name: String,
        val email: String
    )

    // Test builder for validation
    class TestUserBuilder : TestDataBuilder<TestUser> {
        private var id: Long = TestIdGenerator.nextId()
        private var name: String = "Test User"
        private var email: String = uniqueEmail()

        fun withId(id: Long) = apply { this.id = id }
        fun withName(name: String) = apply { this.name = name }
        fun withEmail(email: String) = apply { this.email = email }

        override fun build(): TestUser = TestUser(id, name, email)

        override fun resetDefaults() {
            id = TestIdGenerator.nextId()
            email = uniqueEmail()
        }
    }

    @BeforeTest
    fun resetIdGenerator() {
        TestIdGenerator.reset()
    }

    @Test
    fun `TestIdGenerator should generate sequential IDs`() {
        // When
        val id1 = TestIdGenerator.nextId()
        val id2 = TestIdGenerator.nextId()
        val id3 = TestIdGenerator.nextId()

        // Then
        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
    }

    @Test
    fun `TestIdGenerator reset should start from 1`() {
        // Given
        TestIdGenerator.nextId()
        TestIdGenerator.nextId()
        TestIdGenerator.nextId()

        // When
        TestIdGenerator.reset()
        val id = TestIdGenerator.nextId()

        // Then
        assertEquals(1L, id)
    }

    @Test
    fun `TestDataBuilder buildList should create multiple instances`() {
        // When
        val users = TestUserBuilder().buildList(5)

        // Then
        assertEquals(5, users.size)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), users.map { it.id }.toSet())
    }

    @Test
    fun `TestTimestamps at should return deterministic timestamp`() {
        // When
        val timestamp1 = TestTimestamps.at()
        val timestamp2 = TestTimestamps.at()

        // Then
        assertEquals(timestamp1, timestamp2)
        assertEquals(1609459200000L, timestamp1)
    }

    @Test
    fun `TestTimestamps at with offset should add offset`() {
        // When
        val base = TestTimestamps.at()
        val offset = TestTimestamps.at(1000)

        // Then
        assertEquals(base + 1000, offset)
    }

    @Test
    fun `InMemoryStorage should save and retrieve entity`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()
        val user = TestUser(1L, "Alice", "alice@example.com")

        // When
        storage.save(1L, user)
        val retrieved = storage.findById(1L)

        // Then
        assertEquals(user, retrieved)
    }

    @Test
    fun `InMemoryStorage should return null for non-existent ID`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()

        // When
        val retrieved = storage.findById(999L)

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `InMemoryStorage should findAll returns all entities`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()
        val user1 = TestUser(1L, "Alice", "alice@example.com")
        val user2 = TestUser(2L, "Bob", "bob@example.com")

        // When
        storage.save(1L, user1)
        storage.save(2L, user2)
        val all = storage.findAll()

        // Then
        assertEquals(2, all.size)
        assertTrue(all.contains(user1))
        assertTrue(all.contains(user2))
    }

    @Test
    fun `InMemoryStorage should delete entity`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()
        val user = TestUser(1L, "Alice", "alice@example.com")
        storage.save(1L, user)

        // When
        val deleted = storage.delete(1L)
        val retrieved = storage.findById(1L)

        // Then
        assertTrue(deleted)
        assertNull(retrieved)
    }

    @Test
    fun `InMemoryStorage should count entities`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()
        storage.save(1L, TestUser(1L, "Alice", "alice@example.com"))
        storage.save(2L, TestUser(2L, "Bob", "bob@example.com"))

        // When
        val count = storage.count()

        // Then
        assertEquals(2, count)
    }

    @Test
    fun `InMemoryStorage should deleteAll removes all entities`() {
        // Given
        val storage = InMemoryStorage<Long, TestUser>()
        storage.save(1L, TestUser(1L, "Alice", "alice@example.com"))
        storage.save(2L, TestUser(2L, "Bob", "bob@example.com"))

        // When
        storage.deleteAll()

        // Then
        assertEquals(0, storage.count())
        assertEquals(emptyList(), storage.findAll())
    }

    @Test
    fun `EventHandlerSpy should record events`() {
        // Given
        val spy = EventHandlerSpy<String>()

        // When
        spy.record("event1")
        spy.record("event2")
        spy.record("event3")

        // Then
        assertEquals(3, spy.invocationCount)
        assertEquals(listOf("event1", "event2", "event3"), spy.events)
    }

    @Test
    fun `EventHandlerSpy should track first and last event`() {
        // Given
        val spy = EventHandlerSpy<String>()

        // When
        spy.record("first")
        spy.record("middle")
        spy.record("last")

        // Then
        assertEquals("first", spy.firstEvent)
        assertEquals("last", spy.lastEvent)
    }

    @Test
    fun `EventHandlerSpy should clear events`() {
        // Given
        val spy = EventHandlerSpy<String>()
        spy.record("event1")
        spy.record("event2")

        // When
        spy.clear()

        // Then
        assertEquals(0, spy.invocationCount)
        assertNull(spy.lastEvent)
    }

    @Test
    fun `EventHandlerSpy assertInvocationCount should pass when count matches`() {
        // Given
        val spy = EventHandlerSpy<String>()
        spy.record("event1")
        spy.record("event2")

        // When/Then
        spy.assertInvocationCount(2)
    }

    @Test
    fun `EventHandlerSpy assertInvocationCount should fail when count differs`() {
        // Given
        val spy = EventHandlerSpy<String>()
        spy.record("event1")

        // When/Then
        assertFailsWith<AssertionError> {
            spy.assertInvocationCount(2)
        }
    }

    @Test
    fun `EventHandlerSpy assertInvoked should pass when events recorded`() {
        // Given
        val spy = EventHandlerSpy<String>()
        spy.record("event")

        // When/Then
        spy.assertInvoked()
    }

    @Test
    fun `EventHandlerSpy assertInvoked should fail when no events`() {
        // Given
        val spy = EventHandlerSpy<String>()

        // When/Then
        assertFailsWith<AssertionError> {
            spy.assertInvoked()
        }
    }

    @Test
    fun `EventHandlerSpy assertNotInvoked should pass when no events`() {
        // Given
        val spy = EventHandlerSpy<String>()

        // When/Then
        spy.assertNotInvoked()
    }

    @Test
    fun `EventHandlerSpy assertNotInvoked should fail when events recorded`() {
        // Given
        val spy = EventHandlerSpy<String>()
        spy.record("event")

        // When/Then
        assertFailsWith<AssertionError> {
            spy.assertNotInvoked()
        }
    }
}
