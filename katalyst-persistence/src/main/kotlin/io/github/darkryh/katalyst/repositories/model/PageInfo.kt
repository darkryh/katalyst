package io.github.darkryh.katalyst.repositories.model

/**
 * Page information for paginated results.
 */
data class PageInfo(
    val limit: Int,
    val offset: Int,
    val total: Int
) {
    /**
     * 1-based current page number.
     *
     * A non-positive [limit] cannot express a page size, so the whole result set is
     * treated as a single page rather than dividing by zero (or a negative number).
     */
    val currentPage: Int
        get() = if (limit <= 0) 1 else (offset / limit) + 1

    /**
     * Total number of pages for [total] items at [limit] items per page.
     *
     * A non-positive [limit] is treated as "everything fits on one page": 0 when there
     * are no results, otherwise 1.
     */
    val totalPages: Int
        get() = if (limit <= 0) {
            if (total > 0) 1 else 0
        } else {
            (total + limit - 1) / limit
        }

    val hasNextPage: Boolean
        get() = currentPage < totalPages
}