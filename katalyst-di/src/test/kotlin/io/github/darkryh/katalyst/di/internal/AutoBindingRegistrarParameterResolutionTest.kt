package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AutoBindingRegistrarParameterResolutionTest {
    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
        engine.registerInstance(ConstructorInjectedDependency("resolved"), ConstructorInjectedDependency::class)
    }

    @AfterTest
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `instantiate uses kotlin defaults for unresolved optional constructor parameters`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        val instance = registrar.instantiate(ConstructorWithDefaultScalar::class)

        assertEquals(60, instance.intervalSeconds)
    }

    @Test
    fun `instantiate supplies null for unresolved nullable constructor parameters`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        val instance = registrar.instantiate(ConstructorWithNullableDependency::class)

        assertNull(instance.optional)
    }

    @Test
    fun `instantiate injects direct dependencies through shared resolver`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        val instance = registrar.instantiate(ConstructorWithDependency::class)

        assertEquals("resolved", instance.dependency.label)
    }

    @Test
    fun `instantiate fails clearly for required scalar constructor parameters`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        val error = assertFailsWith<DependencyInjectionException> {
            registrar.instantiate(ConstructorWithRequiredScalar::class)
        }

        assertEquals(
            true,
            error.message.orEmpty().contains("parameter 'intervalSeconds'")
        )
    }
}

private class ConstructorInjectedDependency(val label: String)

private class ConstructorWithDependency(
    val dependency: ConstructorInjectedDependency
)

private class ConstructorWithDefaultScalar(
    val intervalSeconds: Int = 60
)

private class ConstructorWithNullableDependency(
    val optional: MissingConstructorDependency?
)

private class ConstructorWithRequiredScalar(
    val intervalSeconds: Int
)

private class MissingConstructorDependency
