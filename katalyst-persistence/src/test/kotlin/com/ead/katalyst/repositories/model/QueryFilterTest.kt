package com.ead.katalyst.repositories.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for QueryFilter query model.
 *
 * Tests cover:
 * - Default values
 * - Custom configurations
 * - Copy behavior
 */
class QueryFilterTest {

    @Test
    fun `default QueryFilter should have sensible defaults`() {
        // When
        val filter = QueryFilter()

        // Then
        assertEquals(50, filter.limit)
        assertEquals(0, filter.offset)
        assertNull(filter.sortBy)
        assertEquals(SortOrder.ASCENDING, filter.sortOrder)
    }

    @Test
    fun `QueryFilter should accept custom limit`() {
        // When
        val filter = QueryFilter(limit = 100)

        // Then
        assertEquals(100, filter.limit)
        assertEquals(0, filter.offset) // Default
    }

    @Test
    fun `QueryFilter should accept custom offset`() {
        // When
        val filter = QueryFilter(offset = 50)

        // Then
        assertEquals(50, filter.limit) // Default
        assertEquals(50, filter.offset)
    }

    @Test
    fun `QueryFilter should accept custom sortBy`() {
        // When
        val filter = QueryFilter(sortBy = "name")

        // Then
        assertEquals("name", filter.sortBy)
    }

    @Test
    fun `QueryFilter should accept custom sortOrder`() {
        // When
        val filter = QueryFilter(sortOrder = SortOrder.DESCENDING)

        // Then
        assertEquals(SortOrder.DESCENDING, filter.sortOrder)
    }

    @Test
    fun `QueryFilter should accept all custom parameters`() {
        // When
        val filter = QueryFilter(
            limit = 25,
            offset = 100,
            sortBy = "email",
            sortOrder = SortOrder.DESCENDING
        )

        // Then
        assertEquals(25, filter.limit)
        assertEquals(100, filter.offset)
        assertEquals("email", filter.sortBy)
        assertEquals(SortOrder.DESCENDING, filter.sortOrder)
    }

    @Test
    fun `QueryFilter copy should work correctly`() {
        // Given
        val original = QueryFilter(limit = 10, offset = 20)

        // When
        val copied = original.copy(limit = 30)

        // Then
        assertEquals(30, copied.limit)
        assertEquals(20, copied.offset) // Preserved from original
    }

    @Test
    fun `QueryFilter should handle zero limit`() {
        // When
        val filter = QueryFilter(limit = 0)

        // Then
        assertEquals(0, filter.limit)
    }

    @Test
    fun `QueryFilter should handle large offset`() {
        // When
        val filter = QueryFilter(offset = 1000000)

        // Then
        assertEquals(1000000, filter.offset)
    }

    @Test
    fun `QueryFilter should handle null sortBy`() {
        // When
        val filter = QueryFilter(sortBy = null)

        // Then
        assertNull(filter.sortBy)
    }
}
