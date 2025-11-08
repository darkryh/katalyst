package com.ead.katalyst.repositories.models

/**
 * Page information for paginated results.
 */
data class PageInfo(
    val limit: Int,
    val offset: Int,
    val total: Int
) {
    val currentPage: Int
        get() = (offset / limit) + 1

    val totalPages: Int
        get() = (total + limit - 1) / limit

    val hasNextPage: Boolean
        get() = currentPage < totalPages
}