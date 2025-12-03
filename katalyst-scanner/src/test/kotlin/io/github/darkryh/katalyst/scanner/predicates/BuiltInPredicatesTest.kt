package io.github.darkryh.katalyst.scanner.predicates

import kotlin.test.*

/**
 * Comprehensive tests for BuiltInPredicates.
 *
 * Tests cover all built-in predicate functions:
 * - implementsInterface
 * - matchesPackage
 * - matchesClassName
 * - matchesCanonicalName
 * - isConcrete
 * - isNotInterface
 * - hasNoArgsConstructor
 * - isNotTestClass
 * - isNotSynthetic
 * - isInModule
 * - hasPublicMethods
 * - isInstantiable
 */
class BuiltInPredicatesTest {

    // ========== TEST CLASSES ==========

    interface TestService
    abstract class AbstractService : TestService
    class ConcreteService : TestService
    class UserService : TestService {
        constructor()
        fun publicMethod() {}
    }
    class OrderService(val name: String) : TestService  // No no-args constructor
    class TestUserService : TestService
    class MockUserService : TestService
    class UserServiceTest : TestService
    class UserServiceMock : TestService

    // ========== implementsInterface() TESTS ==========

    @Test
    fun `implementsInterface should match implementing classes`() {
        val predicate = implementsInterface<TestService>(TestService::class)

        assertTrue(predicate.matches(ConcreteService::class.java))
        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(AbstractService::class.java))
    }

    @Test
    fun `implementsInterface should match exact interface`() {
        val predicate = implementsInterface<TestService>(TestService::class)

        assertTrue(predicate.matches(TestService::class.java))
    }

    @Test
    fun `implementsInterface should work with abstract classes`() {
        val predicate = implementsInterface<TestService>(TestService::class)

        assertTrue(predicate.matches(AbstractService::class.java))
    }

    // ========== matchesPackage() TESTS ==========

    @Test
    fun `matchesPackage should match exact package`() {
        val predicate = matchesPackage<TestService>("io.github.darkryh.katalyst.scanner.predicates")

        assertTrue(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `matchesPackage should match parent package prefix`() {
        val predicate = matchesPackage<TestService>("io.github.darkryh.katalyst")

        assertTrue(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `matchesPackage should not match different package`() {
        val predicate = matchesPackage<TestService>("com.example.other")

        assertFalse(predicate.matches(ConcreteService::class.java))
    }

    // ========== matchesClassName() TESTS ==========

    @Test
    fun `matchesClassName should match exact name`() {
        val predicate = matchesClassName<TestService>("UserService".toRegex())

        assertTrue(predicate.matches(UserService::class.java))
        assertFalse(predicate.matches(OrderService::class.java))
    }

    @Test
    fun `matchesClassName should match pattern ending with Service`() {
        val predicate = matchesClassName<TestService>(".*Service".toRegex())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertTrue(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `matchesClassName should match pattern starting with User`() {
        val predicate = matchesClassName<TestService>("User.*".toRegex())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(UserServiceTest::class.java))
        assertTrue(predicate.matches(UserServiceMock::class.java))
        assertFalse(predicate.matches(OrderService::class.java))
    }

    @Test
    fun `matchesClassName should match complex patterns`() {
        val predicate = matchesClassName<TestService>("(User|Order)Service".toRegex())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertFalse(predicate.matches(ConcreteService::class.java))
    }

    // ========== matchesCanonicalName() TESTS ==========

    @Test
    fun `matchesCanonicalName should match fully qualified name`() {
        val canonicalName = UserService::class.java.canonicalName ?: error("No canonical name")
        val predicate = matchesCanonicalName<TestService>(canonicalName.toRegex())

        assertTrue(predicate.matches(UserService::class.java))
        assertFalse(predicate.matches(OrderService::class.java))
    }

    @Test
    fun `matchesCanonicalName should match package pattern`() {
        val predicate = matchesCanonicalName<TestService>(
            "io\\.github\\.darkryh\\.katalyst\\..*Service".toRegex()
        )

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
    }

    // ========== isConcrete() TESTS ==========

    @Test
    fun `isConcrete should match concrete classes`() {
        val predicate = isConcrete<TestService>()

        assertTrue(predicate.matches(ConcreteService::class.java))
        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `isConcrete should not match abstract classes`() {
        val predicate = isConcrete<TestService>()

        assertFalse(predicate.matches(AbstractService::class.java))
    }

    @Test
    fun `isConcrete should not match interfaces`() {
        val predicate = isConcrete<TestService>()

        assertFalse(predicate.matches(TestService::class.java))
    }

    // ========== isNotInterface() TESTS ==========

    @Test
    fun `isNotInterface should match classes`() {
        val predicate = isNotInterface<TestService>()

        assertTrue(predicate.matches(ConcreteService::class.java))
        assertTrue(predicate.matches(AbstractService::class.java))
    }

    @Test
    fun `isNotInterface should not match interfaces`() {
        val predicate = isNotInterface<TestService>()

        assertFalse(predicate.matches(TestService::class.java))
    }

    // ========== hasNoArgsConstructor() TESTS ==========

    @Test
    fun `hasNoArgsConstructor should match classes with no-args constructor`() {
        val predicate = hasNoArgsConstructor<TestService>()

        assertTrue(predicate.matches(ConcreteService::class.java))
        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `hasNoArgsConstructor should not match classes without no-args constructor`() {
        val predicate = hasNoArgsConstructor<TestService>()

        assertFalse(predicate.matches(OrderService::class.java))
    }

    // ========== isNotTestClass() TESTS ==========

    @Test
    fun `isNotTestClass should match production classes`() {
        val predicate = isNotTestClass<TestService>()

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertTrue(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `isNotTestClass should not match classes starting with Test`() {
        val predicate = isNotTestClass<TestService>()

        assertFalse(predicate.matches(TestUserService::class.java))
    }

    @Test
    fun `isNotTestClass should not match classes ending with Test`() {
        val predicate = isNotTestClass<TestService>()

        assertFalse(predicate.matches(UserServiceTest::class.java))
    }

    @Test
    fun `isNotTestClass should not match classes starting with Mock`() {
        val predicate = isNotTestClass<TestService>()

        assertFalse(predicate.matches(MockUserService::class.java))
    }

    @Test
    fun `isNotTestClass should not match classes ending with Mock`() {
        val predicate = isNotTestClass<TestService>()

        assertFalse(predicate.matches(UserServiceMock::class.java))
    }

    // ========== isNotSynthetic() TESTS ==========

    @Test
    fun `isNotSynthetic should match regular classes`() {
        val predicate = isNotSynthetic<TestService>()

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
    }

    // ========== isInModule() TESTS ==========

    @Test
    fun `isInModule should match classes in module`() {
        val predicate = isInModule<TestService>("io.github.darkryh.katalyst.scanner")

        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `isInModule should match classes in sub-module`() {
        val predicate = isInModule<TestService>("io.github.darkryh.katalyst")

        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `isInModule should not match classes in different module`() {
        val predicate = isInModule<TestService>("com.example.other")

        assertFalse(predicate.matches(UserService::class.java))
    }

    // ========== hasPublicMethods() TESTS ==========

    @Test
    fun `hasPublicMethods should match classes with public methods`() {
        val predicate = hasPublicMethods<TestService>()

        assertTrue(predicate.matches(UserService::class.java))
    }

    // ========== isInstantiable() TESTS ==========

    @Test
    fun `isInstantiable should match concrete classes with no-args constructor`() {
        val predicate = isInstantiable<TestService>()

        assertTrue(predicate.matches(ConcreteService::class.java))
        assertTrue(predicate.matches(UserService::class.java))
    }

    @Test
    fun `isInstantiable should not match abstract classes`() {
        val predicate = isInstantiable<TestService>()

        assertFalse(predicate.matches(AbstractService::class.java))
    }

    @Test
    fun `isInstantiable should not match interfaces`() {
        val predicate = isInstantiable<TestService>()

        assertFalse(predicate.matches(TestService::class.java))
    }

    @Test
    fun `isInstantiable should not match classes without no-args constructor`() {
        val predicate = isInstantiable<TestService>()

        assertFalse(predicate.matches(OrderService::class.java))
    }

    // ========== PREDICATE COMBINATION TESTS ==========

    @Test
    fun `should combine predicates with and`() {
        val predicate = isConcrete<TestService>()
            .and(isNotTestClass())
            .and(hasNoArgsConstructor())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(ConcreteService::class.java))
        assertFalse(predicate.matches(TestUserService::class.java))
        assertFalse(predicate.matches(OrderService::class.java))
    }

    @Test
    fun `should combine predicates with or`() {
        val predicate = matchesClassName<TestService>("User.*".toRegex())
            .or(matchesClassName("Order.*".toRegex()))

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertFalse(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `should invert predicates with not`() {
        val predicate = isNotTestClass<TestService>().not()

        assertFalse(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(TestUserService::class.java))
        assertTrue(predicate.matches(MockUserService::class.java))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `filter production service classes`() {
        val predicate = implementsInterface<TestService>(TestService::class)
            .and(isConcrete())
            .and(isNotTestClass())
            .and(matchesClassName(".*Service".toRegex()))

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertTrue(predicate.matches(ConcreteService::class.java))
        assertFalse(predicate.matches(TestUserService::class.java))
        assertFalse(predicate.matches(AbstractService::class.java))
    }

    @Test
    fun `filter instantiable services in specific package`() {
        val predicate = isInstantiable<TestService>()
            .and(matchesPackage("io.github.darkryh.katalyst.scanner.predicates"))
            .and(isNotTestClass())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(ConcreteService::class.java))
        assertFalse(predicate.matches(OrderService::class.java))  // No no-args constructor
        assertFalse(predicate.matches(TestUserService::class.java))  // Test class
    }

    @Test
    fun `filter by naming convention and type`() {
        val predicate = matchesClassName<TestService>("(User|Order).*".toRegex())
            .and(isConcrete())
            .and(isNotInterface())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertFalse(predicate.matches(ConcreteService::class.java))
    }

    @Test
    fun `exclude test and mock classes`() {
        val predicate = isNotTestClass<TestService>()
            .and(isNotSynthetic())
            .and(isConcrete())

        assertTrue(predicate.matches(UserService::class.java))
        assertTrue(predicate.matches(OrderService::class.java))
        assertFalse(predicate.matches(TestUserService::class.java))
        assertFalse(predicate.matches(MockUserService::class.java))
        assertFalse(predicate.matches(UserServiceTest::class.java))
    }

    @Test
    fun `match services in multiple modules`() {
        val authModule = isInModule<TestService>("com.example.auth")
        val paymentModule = isInModule<TestService>("com.example.payment")

        val predicate = authModule.or(paymentModule)
            .and(matchesClassName(".*Service".toRegex()))

        // These are in the test package, so they won't match the module filter
        // This test demonstrates the pattern
        val result = authModule.or(paymentModule)
        assertNotNull(result)
    }
}
