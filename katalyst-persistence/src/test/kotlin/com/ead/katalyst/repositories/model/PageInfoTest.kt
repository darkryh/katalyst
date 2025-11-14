package com.ead.katalyst.repositories.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PageInfo pagination model.
 *
 * Tests cover:
 * - Current page calculation
 * - Total pages calculation
 * - hasNextPage logic
 * - Edge cases (empty results, large datasets)
 */
class PageInfoTest {

    @Test
    fun `currentPage should be 1 for first page`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 100)

        // When/Then
        assertEquals(1, pageInfo.currentPage)
    }

    @Test
    fun `currentPage should be 2 for second page`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 10, total = 100)

        // When/Then
        assertEquals(2, pageInfo.currentPage)
    }

    @Test
    fun `currentPage should be 5 for offset 40 with limit 10`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 40, total = 100)

        // When/Then
        assertEquals(5, pageInfo.currentPage)
    }

    @Test
    fun `totalPages should be calculated correctly`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 100)

        // When/Then
        assertEquals(10, pageInfo.totalPages)
    }

    @Test
    fun `totalPages should round up for partial page`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 95)

        // When/Then
        assertEquals(10, pageInfo.totalPages) // 95/10 = 9.5 -> 10 pages
    }

    @Test
    fun `totalPages should be 1 when total equals limit`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 10)

        // When/Then
        assertEquals(1, pageInfo.totalPages)
    }

    @Test
    fun `totalPages should be 0 when total is 0`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 0)

        // When/Then
        assertEquals(0, pageInfo.totalPages)
    }

    @Test
    fun `hasNextPage should be true when more pages exist`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 100)

        // When/Then
        assertTrue(pageInfo.hasNextPage)
    }

    @Test
    fun `hasNextPage should be false on last page`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 90, total = 100)

        // When/Then
        assertFalse(pageInfo.hasNextPage)
    }

    @Test
    fun `hasNextPage should be false when total is 0`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 0, total = 0)

        // When/Then
        assertFalse(pageInfo.hasNextPage)
    }

    @Test
    fun `hasNextPage should be false when on single page`() {
        // Given
        val pageInfo = PageInfo(limit = 50, offset = 0, total = 30)

        // When/Then
        assertFalse(pageInfo.hasNextPage)
    }

    @Test
    fun `pagination with limit 1 should work correctly`() {
        // Given
        val pageInfo = PageInfo(limit = 1, offset = 5, total = 10)

        // When/Then
        assertEquals(6, pageInfo.currentPage)
        assertEquals(10, pageInfo.totalPages)
        assertTrue(pageInfo.hasNextPage)
    }

    @Test
    fun `pagination with large dataset should work correctly`() {
        // Given
        val pageInfo = PageInfo(limit = 20, offset = 1980, total = 10000)

        // When/Then
        assertEquals(100, pageInfo.currentPage)
        assertEquals(500, pageInfo.totalPages)
        assertTrue(pageInfo.hasNextPage)
    }

    @Test
    fun `pagination with offset beyond total should still calculate correctly`() {
        // Given
        val pageInfo = PageInfo(limit = 10, offset = 200, total = 100)

        // When/Then
        assertEquals(21, pageInfo.currentPage)
        assertEquals(10, pageInfo.totalPages)
        assertFalse(pageInfo.hasNextPage) // Beyond last page
    }

    @Test
    fun `pagination with non-divisible total should round up totalPages`() {
        // Given
        val pageInfo = PageInfo(limit = 7, offset = 0, total = 50)

        // When/Then
        assertEquals(8, pageInfo.totalPages) // 50/7 = 7.14 -> 8 pages
    }

    @Test
    fun `last page with partial results should indicate no next page`() {
        // Given - Total 25 items, limit 10, last page has 5 items
        val pageInfo = PageInfo(limit = 10, offset = 20, total = 25)

        // When/Then
        assertEquals(3, pageInfo.currentPage)
        assertEquals(3, pageInfo.totalPages)
        assertFalse(pageInfo.hasNextPage)
    }
}
