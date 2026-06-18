package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializerRegistry
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializerRegistry
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AutoBindingRegistrarInitializerMultibindingTest {
    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
        AutoBindingRegistrar.resetSecondaryTypeTracking()
        RegistryManager.resetAll()
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        engine.stop()
    }

    @Test
    fun `allows multiple ApplicationInitializer implementations without collision`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        registrar.registerInstance(FirstInitializer(), FirstInitializer::class, listOf(ApplicationInitializer::class))
        registrar.registerInstance(SecondInitializer(), SecondInitializer::class, listOf(ApplicationInitializer::class))

        val discovered = ApplicationInitializerRegistry.getAll().map { it::class }.toSet()
        assertEquals(setOf(FirstInitializer::class, SecondInitializer::class), discovered)
    }

    @Test
    fun `allows multiple ApplicationReadyInitializer implementations without collision`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        registrar.registerInstance(FirstRuntimeReadyInitializer(), FirstRuntimeReadyInitializer::class, listOf(ApplicationReadyInitializer::class))
        registrar.registerInstance(SecondRuntimeReadyInitializer(), SecondRuntimeReadyInitializer::class, listOf(ApplicationReadyInitializer::class))

        val discovered = ApplicationReadyInitializerRegistry.getAll().map { it::class }.toSet()
        assertEquals(setOf(FirstRuntimeReadyInitializer::class, SecondRuntimeReadyInitializer::class), discovered)
    }

    @Test
    fun `keeps strict collision behavior for non-multibinding secondary interfaces`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        registrar.registerInstance(FirstFooImpl(), FirstFooImpl::class, listOf(FooContract::class))

        assertFailsWith<DependencyInjectionException> {
            registrar.registerInstance(SecondFooImpl(), SecondFooImpl::class, listOf(FooContract::class))
        }
    }
}

private interface FooContract

private class FirstFooImpl : FooContract

private class SecondFooImpl : FooContract

private class FirstInitializer : ApplicationInitializer {
    override val initializerId: String = "FirstInitializer"
    override suspend fun onApplicationReady() = Unit
}

private class SecondInitializer : ApplicationInitializer {
    override val initializerId: String = "SecondInitializer"
    override suspend fun onApplicationReady() = Unit
}

private class FirstRuntimeReadyInitializer : ApplicationReadyInitializer {
    override val initializerId: String = "FirstRuntimeReadyInitializer"
    override suspend fun onRuntimeReady() = Unit
}

private class SecondRuntimeReadyInitializer : ApplicationReadyInitializer {
    override val initializerId: String = "SecondRuntimeReadyInitializer"
    override suspend fun onRuntimeReady() = Unit
}
