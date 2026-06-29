package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.di.lifecycle.StartupHookRegistry
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.ReadyHookRegistry
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
    fun `allows multiple StartupHook implementations without collision`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        registrar.registerInstance(FirstInitializer(), FirstInitializer::class, listOf(StartupHook::class))
        registrar.registerInstance(SecondInitializer(), SecondInitializer::class, listOf(StartupHook::class))

        val discovered = StartupHookRegistry.getAll().map { it::class }.toSet()
        assertEquals(setOf(FirstInitializer::class, SecondInitializer::class), discovered)
    }

    @Test
    fun `allows multiple ReadyHook implementations without collision`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, emptyArray())

        registrar.registerInstance(FirstRuntimeReadyInitializer(), FirstRuntimeReadyInitializer::class, listOf(ReadyHook::class))
        registrar.registerInstance(SecondRuntimeReadyInitializer(), SecondRuntimeReadyInitializer::class, listOf(ReadyHook::class))

        val discovered = ReadyHookRegistry.getAll().map { it::class }.toSet()
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

private class FirstInitializer : StartupHook {
    override val id: String = "FirstInitializer"
    override suspend fun onStartup() = Unit
}

private class SecondInitializer : StartupHook {
    override val id: String = "SecondInitializer"
    override suspend fun onStartup() = Unit
}

private class FirstRuntimeReadyInitializer : ReadyHook {
    override val id: String = "FirstRuntimeReadyInitializer"
    override suspend fun onReady() = Unit
}

private class SecondRuntimeReadyInitializer : ReadyHook {
    override val id: String = "SecondRuntimeReadyInitializer"
    override suspend fun onReady() = Unit
}
