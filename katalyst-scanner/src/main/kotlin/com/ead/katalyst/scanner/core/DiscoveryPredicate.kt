package com.ead.katalyst.scanner.core

/**
 * Flexible filtering mechanism for discovery results.
 *
 * A predicate determines whether a discovered class matches your criteria.
 * Multiple predicates can be combined using `and()` and `or()`.
 *
 * **Type Parameter T:**
 * - `T` is the base type that discovered classes must match
 * - The predicate itself operates on generic Class objects
 *
 * **Usage Examples:**
 * ```kotlin
 * // Simple predicate: matches all Service implementations
 * val allServices = DiscoveryPredicate<Service> { true }
 *
 * // Match only classes NOT starting with "Test"
 * val noTests = DiscoveryPredicate<Service> {
 *     !it.simpleName.startsWith("Test")
 * }
 *
 * // Combine predicates
 * val combined = noTests.and(
 *     DiscoveryPredicate { it.packageName.startsWith("com.ead") }
 * )
 * ```
 *
 * @param T The base type or marker interface
 */
fun interface DiscoveryPredicate<T> {
    /**
     * Tests whether a class matches this predicate.
     *
     * @param clazz The class to test
     * @return true if the class matches this predicate, false otherwise
     */
    fun matches(clazz: Class<*>): Boolean

    /**
     * Combines this predicate with another using AND logic.
     * Both predicates must be true.
     */
    fun and(other: DiscoveryPredicate<T>): DiscoveryPredicate<T> {
        return DiscoveryPredicate { clazz ->
            this.matches(clazz) && other.matches(clazz)
        }
    }

    /**
     * Combines this predicate with another using OR logic.
     * At least one predicate must be true.
     */
    fun or(other: DiscoveryPredicate<T>): DiscoveryPredicate<T> {
        return DiscoveryPredicate { clazz ->
            this.matches(clazz) || other.matches(clazz)
        }
    }

    /**
     * Inverts this predicate (NOT logic).
     * Matches everything this predicate does NOT match.
     */
    fun not(): DiscoveryPredicate<T> {
        return DiscoveryPredicate { clazz ->
            !this.matches(clazz)
        }
    }

    companion object {
        /**
         * Creates a predicate that always returns true (matches everything).
         */
        fun <T> all(): DiscoveryPredicate<T> {
            return DiscoveryPredicate { true }
        }

        /**
         * Creates a predicate that always returns false (matches nothing).
         */
        fun <T> none(): DiscoveryPredicate<T> {
            return DiscoveryPredicate { false }
        }

        /**
         * Creates a predicate from a lambda.
         */
        fun <T> create(matcher: (Class<*>) -> Boolean): DiscoveryPredicate<T> {
            return DiscoveryPredicate(matcher)
        }
    }
}
