package com.ead.katalyst.repositories.model

// ============= Query Support =============

/**
 * Query builder for filtering and pagination.
 */
data class QueryFilter(
    val limit: Int = 50,
    val offset: Int = 0,
    val sortBy: String? = null,
    val sortOrder: SortOrder = SortOrder.ASCENDING
)