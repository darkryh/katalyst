package io.github.darkryh.katalyst.di.invocation

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression tests for [ParameterResolver] parameter resolution behavior.
 *
 * Covers finding A: a real construction/resolution failure from the container must
 * surface with its original cause (not be swallowed into a generic, cause-less error),
 * while the legitimate "binding genuinely absent -> fall back to default/null" path
 * must keep working unchanged.
 */
class ParameterResolverTest {

    class RequiredDependency

    class ComponentUnderTest(val dependency: RequiredDependency)
    class ComponentWithNullableDependency(val dependency: RequiredDependency?)

    /** Container whose get() always fails with a genuine (non-"not found") failure. */
    private class FailingContainer(private val failure: Throwable) : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T = throw failure
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = throw failure
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }

    /** Container simulating a genuinely absent binding (the legitimate fallback path). */
    private class EmptyContainer : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            throw NoSuchElementException("No binding registered for ${type.simpleName}")
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }

    @Test
    fun `real construction failure surfaces with its cause instead of a masked generic error`() {
        val realFailure = IllegalStateException("boom while constructing RequiredDependency")
        val resolver = ParameterResolver(FailingContainer(realFailure))
        val constructor = ComponentUnderTest::class.primaryConstructor!!

        val thrown = assertFailsWith<DependencyInjectionException> {
            resolver.resolveParameters(constructor, ownerDescription = "ComponentUnderTest")
        }

        assertSame(
            realFailure,
            thrown.cause,
            "The real container failure must surface as the exception cause, not be swallowed"
        )
    }

    @Test
    fun `genuinely absent binding for a nullable parameter still falls back to null without error`() {
        val resolver = ParameterResolver(EmptyContainer())
        val constructor = ComponentWithNullableDependency::class.primaryConstructor!!

        val args = resolver.resolveParameters(constructor, ownerDescription = "ComponentWithNullableDependency")

        val param = constructor.parameters.first { it.name == "dependency" }
        assertNull(args[param])
    }
}
