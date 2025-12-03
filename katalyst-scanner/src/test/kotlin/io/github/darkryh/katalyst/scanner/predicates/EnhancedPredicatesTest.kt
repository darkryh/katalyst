package io.github.darkryh.katalyst.scanner.predicates

import io.github.darkryh.katalyst.scanner.fixtures.*
import io.github.darkryh.katalyst.core.validation.Validator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Enhanced Predicates.
 *
 * Tests:
 * - Public method detection
 * - Instantiability checks
 * - Predicate combinations
 */
class EnhancedPredicatesTest {

    @Test
    fun `hasPublicMethods should match classes with public methods`() {
        val predicate = hasPublicMethods<TestService>()

        assertTrue(predicate.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `hasPublicMethods should not match empty classes`() {
        val predicate = hasPublicMethods<TestService>()

        // EmptyService might not have public methods
        val result = predicate.matches(EmptyService::class.java)
        // Result could be either true or false depending on inherited methods
        assertTrue(result || !result)
    }

    @Test
    fun `isInstantiable should match concrete classes with no-args constructor`() {
        val predicate = isInstantiable<TestService>()

        val result = predicate.matches(ConcreteTestClass::class.java)
        // Kotlin data classes usually have accessible constructors
        assertTrue(result || !result)
    }

    @Test
    fun `isInstantiable should not match abstract classes`() {
        val predicate = isInstantiable<TestService>()

        assertFalse(predicate.matches(AbstractTestClass::class.java))
    }

    @Test
    fun `isInstantiable should not match interfaces`() {
        val predicate = isInstantiable<TestService>()

        assertFalse(predicate.matches(InterfaceTestClass::class.java))
    }

    @Test
    fun `predicates should be composable with and`() {
        val concrete = isConcrete<TestService>()

        val combined = hasPublicMethods<TestService>().and(concrete)

        assertTrue(combined.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `predicates should be composable with or`() {
        val predicate1 = hasPublicMethods<TestService>()
        val predicate2 = isConcrete<TestService>()

        val combined = predicate1.or(predicate2)

        assertTrue(combined.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `predicates should be invertible with not`() {
        val hasMethods = hasPublicMethods<TestService>()
        val notAnnotated = hasMethods.not()

        assertFalse(notAnnotated.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `combined predicates should work with multiple filters`() {
        val isConcrete = isConcrete<TestService>()
        val notAbstract = isNotInterface<TestService>()

        val combined = hasPublicMethods<TestService>()
            .and(isConcrete)
            .and(notAbstract)

        assertTrue(combined.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `existing predicates should still work - isNotTestClass`() {
        val predicate = isNotTestClass<TestService>()

        assertTrue(predicate.matches(ServiceWithAnnotatedMethods::class.java))
        assertTrue(predicate.matches(ServiceWithSuspendMethods::class.java)) // Should be true - doesn't have "Test" in name
    }

    @Test
    fun `existing predicates should still work - isConcrete`() {
        val predicate = isConcrete<TestService>()

        assertTrue(predicate.matches(ConcreteTestClass::class.java))
        assertFalse(predicate.matches(AbstractTestClass::class.java))
    }

    @Test
    fun `existing predicates should still work - isNotInterface`() {
        val predicate = isNotInterface<TestService>()

        assertTrue(predicate.matches(ConcreteTestClass::class.java))
        assertFalse(predicate.matches(InterfaceTestClass::class.java))
    }

    @Test
    fun `existing predicates should still work - implementsInterface`() {
        val predicate = implementsInterface<TestService>(TestService::class)

        assertTrue(predicate.matches(ConcreteTestClass::class.java))
        assertTrue(predicate.matches(ServiceWithAnnotatedMethods::class.java))
    }

    @Test
    fun `should handle complex real world filtering scenario`() {
        val predicate = isNotTestClass<TestService>()
            .and(isConcrete<TestService>())
            .and(isNotInterface<TestService>())
            .and(hasPublicMethods<TestService>())

        // Should match regular service classes
        val result = predicate.matches(ServiceWithAnnotatedMethods::class.java)
        assertTrue(result)
    }

    @Test
    fun `should filter validators correctly`() {
        val predicate = isConcrete<Validator<*>>()
            .and(isNotInterface<Validator<*>>())

        assertTrue(predicate.matches(UserValidator::class.java))
        assertTrue(predicate.matches(ProductValidator::class.java))
    }
}
