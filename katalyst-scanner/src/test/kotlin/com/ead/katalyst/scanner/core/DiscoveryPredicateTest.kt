package com.ead.katalyst.scanner.core

import kotlin.test.*

/**
 * Comprehensive tests for DiscoveryPredicate.
 *
 * Tests cover:
 * - Predicate creation and matching
 * - Combinator methods (and, or, not)
 * - Factory methods (all, none, create)
 * - Practical filtering scenarios
 */
class DiscoveryPredicateTest {

    interface TestService
    class UserService : TestService
    class OrderService : TestService
    class TestUserService : TestService
    class MockOrderService : TestService

    // ========== BASIC PREDICATE TESTS ==========

    @Test
    fun `Predicate should match when lambda returns true`() {
        val predicate = DiscoveryPredicate<TestService> { true }
        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `Predicate should not match when lambda returns false`() {
        val predicate = DiscoveryPredicate<TestService> { false }
        assertFalse(predicate.matches(UserService::class.java))
    }

    @Test
    fun `Predicate should receive correct class parameter`() {
        var receivedClass: Class<*>? = null
        val predicate = DiscoveryPredicate<TestService> { clazz ->
            receivedClass = clazz
            true
        }

        predicate.matches(UserService::class.java)
        assertEquals(UserService::class.java, receivedClass)
    }

    // ========== AND COMBINATOR TESTS ==========

    @Test
    fun `and should require both predicates to be true`() {
        val pred1 = DiscoveryPredicate<TestService> { it.simpleName.contains("Service") }
        val pred2 = DiscoveryPredicate<TestService> { it.simpleName.startsWith("User") }

        val combined = pred1.and(pred2)

        assertTrue(combined.matches(UserService::class.java))
        assertFalse(combined.matches(OrderService::class.java))  // Fails pred2
    }

    @Test
    fun `and should return false if first predicate fails`() {
        val pred1 = DiscoveryPredicate<TestService> { false }
        val pred2 = DiscoveryPredicate<TestService> { true }

        val combined = pred1.and(pred2)

        assertFalse(combined.matches(UserService::class.java))
    }

    @Test
    fun `and should return false if second predicate fails`() {
        val pred1 = DiscoveryPredicate<TestService> { true }
        val pred2 = DiscoveryPredicate<TestService> { false }

        val combined = pred1.and(pred2)

        assertFalse(combined.matches(UserService::class.java))
    }

    @Test
    fun `and should chain multiple predicates`() {
        val pred1 = DiscoveryPredicate<TestService> { it.simpleName.contains("Service") }
        val pred2 = DiscoveryPredicate<TestService> { it.simpleName.length > 5 }
        val pred3 = DiscoveryPredicate<TestService> { !it.simpleName.startsWith("Test") }

        val combined = pred1.and(pred2).and(pred3)

        assertTrue(combined.matches(UserService::class.java))
        assertFalse(combined.matches(TestUserService::class.java))  // Fails pred3
    }

    // ========== OR COMBINATOR TESTS ==========

    @Test
    fun `or should return true if first predicate matches`() {
        val pred1 = DiscoveryPredicate<TestService> { true }
        val pred2 = DiscoveryPredicate<TestService> { false }

        val combined = pred1.or(pred2)

        assertTrue(combined.matches(UserService::class.java))
    }

    @Test
    fun `or should return true if second predicate matches`() {
        val pred1 = DiscoveryPredicate<TestService> { false }
        val pred2 = DiscoveryPredicate<TestService> { true }

        val combined = pred1.or(pred2)

        assertTrue(combined.matches(UserService::class.java))
    }

    @Test
    fun `or should return true if both predicates match`() {
        val pred1 = DiscoveryPredicate<TestService> { true }
        val pred2 = DiscoveryPredicate<TestService> { true }

        val combined = pred1.or(pred2)

        assertTrue(combined.matches(UserService::class.java))
    }

    @Test
    fun `or should return false if both predicates fail`() {
        val pred1 = DiscoveryPredicate<TestService> { false }
        val pred2 = DiscoveryPredicate<TestService> { false }

        val combined = pred1.or(pred2)

        assertFalse(combined.matches(UserService::class.java))
    }

    @Test
    fun `or should match either condition`() {
        val pred1 = DiscoveryPredicate<TestService> { it.simpleName.startsWith("User") }
        val pred2 = DiscoveryPredicate<TestService> { it.simpleName.startsWith("Order") }

        val combined = pred1.or(pred2)

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
        assertFalse(combined.matches(TestUserService::class.java))
    }

    @Test
    fun `or should chain multiple predicates`() {
        val pred1 = DiscoveryPredicate<TestService> { it.simpleName == "UserService" }
        val pred2 = DiscoveryPredicate<TestService> { it.simpleName == "OrderService" }
        val pred3 = DiscoveryPredicate<TestService> { it.simpleName == "TestUserService" }

        val combined = pred1.or(pred2).or(pred3)

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
        assertTrue(combined.matches(TestUserService::class.java))
        assertFalse(combined.matches(MockOrderService::class.java))
    }

    // ========== NOT COMBINATOR TESTS ==========

    @Test
    fun `not should invert predicate result`() {
        val predicate = DiscoveryPredicate<TestService> { it.simpleName.startsWith("User") }
        val inverted = predicate.not()

        assertFalse(inverted.matches(UserService::class.java))
        assertTrue(inverted.matches(OrderService::class.java))
    }

    @Test
    fun `not should convert true to false`() {
        val predicate = DiscoveryPredicate<TestService> { true }
        val inverted = predicate.not()

        assertFalse(inverted.matches(UserService::class.java))
    }

    @Test
    fun `not should convert false to true`() {
        val predicate = DiscoveryPredicate<TestService> { false }
        val inverted = predicate.not()

        assertTrue(inverted.matches(UserService::class.java))
    }

    @Test
    fun `not should work with complex predicates`() {
        val predicate = DiscoveryPredicate<TestService> {
            it.simpleName.startsWith("Test") || it.simpleName.startsWith("Mock")
        }
        val inverted = predicate.not()

        assertTrue(inverted.matches(UserService::class.java))
        assertTrue(inverted.matches(OrderService::class.java))
        assertFalse(inverted.matches(TestUserService::class.java))
        assertFalse(inverted.matches(MockOrderService::class.java))
    }

    // ========== FACTORY METHOD TESTS ==========

    @Test
    fun `all should match everything`() {
        val predicate = DiscoveryPredicate.all<TestService>()

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertTrue(predicate.matches(TestUserService::class.java))
        assertTrue(predicate.matches(MockOrderService::class.java))
    }

    @Test
    fun `none should match nothing`() {
        val predicate = DiscoveryPredicate.none<TestService>()

        assertFalse(predicate.matches(UserService::class.java))
        assertFalse(predicate.matches(OrderService::class.java))
        assertFalse(predicate.matches(TestUserService::class.java))
    }

    @Test
    fun `create should build predicate from lambda`() {
        val predicate = DiscoveryPredicate.create<TestService> {
            it.simpleName.length > 10
        }

        assertTrue(predicate.matches(TestUserService::class.java))  // 15 chars
        assertFalse(predicate.matches(UserService::class.java))  // 11 chars
    }

    // ========== COMPLEX COMBINATION TESTS ==========

    @Test
    fun `should combine and with or`() {
        val isService = DiscoveryPredicate<TestService> { it.simpleName.contains("Service") }
        val startsWithUser = DiscoveryPredicate<TestService> { it.simpleName.startsWith("User") }
        val startsWithOrder = DiscoveryPredicate<TestService> { it.simpleName.startsWith("Order") }

        val combined = isService.and(startsWithUser.or(startsWithOrder))

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
        assertFalse(combined.matches(TestUserService::class.java))  // Starts with "Test"
    }

    @Test
    fun `should combine with not and and`() {
        val isService = DiscoveryPredicate<TestService> { it.simpleName.contains("Service") }
        val isTest = DiscoveryPredicate<TestService> { it.simpleName.startsWith("Test") }

        val combined = isService.and(isTest.not())

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
        assertFalse(combined.matches(TestUserService::class.java))
    }

    @Test
    fun `should create complex filtering logic`() {
        val isService = DiscoveryPredicate<TestService> { it.simpleName.endsWith("Service") }
        val isTest = DiscoveryPredicate<TestService> { it.simpleName.startsWith("Test") }
        val isMock = DiscoveryPredicate<TestService> { it.simpleName.startsWith("Mock") }

        // Match services that are NOT test or mock classes
        val combined = isService.and(isTest.not()).and(isMock.not())

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
        assertFalse(combined.matches(TestUserService::class.java))
        assertFalse(combined.matches(MockOrderService::class.java))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `filter by package and class name pattern`() {
        val inExamplePackage = DiscoveryPredicate<TestService> {
            it.packageName.startsWith("com.ead.katalyst.scanner")
        }
        val endsWithService = DiscoveryPredicate<TestService> {
            it.simpleName.endsWith("Service")
        }

        val combined = inExamplePackage.and(endsWithService)

        assertTrue(combined.matches(UserService::class.java))
        assertTrue(combined.matches(OrderService::class.java))
    }

    @Test
    fun `exclude test classes`() {
        val notTest = DiscoveryPredicate<TestService> {
            !it.simpleName.startsWith("Test") &&
                    !it.simpleName.endsWith("Test") &&
                    !it.simpleName.startsWith("Mock")
        }

        assertTrue(notTest.matches(UserService::class.java))
        assertTrue(notTest.matches(OrderService::class.java))
        assertFalse(notTest.matches(TestUserService::class.java))
        assertFalse(notTest.matches(MockOrderService::class.java))
    }

    @Test
    fun `match specific naming conventions`() {
        val matchesConvention = DiscoveryPredicate<TestService> {
            val name = it.simpleName
            name.endsWith("Service") ||
                    name.endsWith("Repository") ||
                    name.endsWith("Controller")
        }

        assertTrue(matchesConvention.matches(UserService::class.java))
        assertTrue(matchesConvention.matches(OrderService::class.java))
    }

    @Test
    fun `whitelist specific classes`() {
        val allowedNames = setOf("UserService", "OrderService")
        val whitelist = DiscoveryPredicate<TestService> {
            allowedNames.contains(it.simpleName)
        }

        assertTrue(whitelist.matches(UserService::class.java))
        assertTrue(whitelist.matches(OrderService::class.java))
        assertFalse(whitelist.matches(TestUserService::class.java))
    }

    @Test
    fun `blacklist specific patterns`() {
        val blacklistedPatterns = listOf("Test", "Mock", "Stub", "Fake")
        val blacklist = DiscoveryPredicate<TestService> {
            blacklistedPatterns.none { pattern ->
                it.simpleName.startsWith(pattern)
            }
        }

        assertTrue(blacklist.matches(UserService::class.java))
        assertFalse(blacklist.matches(TestUserService::class.java))
        assertFalse(blacklist.matches(MockOrderService::class.java))
    }

    @Test
    fun `conditional filtering based on class properties`() {
        val predicate = DiscoveryPredicate<TestService> {
            val name = it.simpleName
            when {
                name.length < 5 -> false
                name.contains("$") -> false  // Synthetic classes
                !name[0].isUpperCase() -> false  // Must start with capital
                else -> true
            }
        }

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
    }
}
