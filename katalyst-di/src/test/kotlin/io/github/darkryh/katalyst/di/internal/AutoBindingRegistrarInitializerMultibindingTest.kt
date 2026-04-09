package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializer
import io.github.darkryh.katalyst.di.lifecycle.ApplicationInitializerRegistry
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializer
import io.github.darkryh.katalyst.di.lifecycle.ApplicationReadyInitializerRegistry
import io.github.darkryh.katalyst.di.registry.RegistryManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.Koin

class AutoBindingRegistrarInitializerMultibindingTest {
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        startKoin { }
        koin = GlobalContext.get()
        AutoBindingRegistrar.resetSecondaryTypeTracking()
        RegistryManager.resetAll()
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        stopKoin()
    }

    @Test
    fun `allows multiple ApplicationInitializer implementations without collision`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())

        registrar.registerInstanceWithKoin(FirstInitializer(), FirstInitializer::class, listOf(ApplicationInitializer::class))
        registrar.registerInstanceWithKoin(SecondInitializer(), SecondInitializer::class, listOf(ApplicationInitializer::class))

        val discovered = ApplicationInitializerRegistry.getAll().map { it::class }.toSet()
        assertEquals(setOf(FirstInitializer::class, SecondInitializer::class), discovered)
    }

    @Test
    fun `allows multiple ApplicationReadyInitializer implementations without collision`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())

        registrar.registerInstanceWithKoin(FirstRuntimeReadyInitializer(), FirstRuntimeReadyInitializer::class, listOf(ApplicationReadyInitializer::class))
        registrar.registerInstanceWithKoin(SecondRuntimeReadyInitializer(), SecondRuntimeReadyInitializer::class, listOf(ApplicationReadyInitializer::class))

        val discovered = ApplicationReadyInitializerRegistry.getAll().map { it::class }.toSet()
        assertEquals(setOf(FirstRuntimeReadyInitializer::class, SecondRuntimeReadyInitializer::class), discovered)
    }

    @Test
    fun `keeps strict collision behavior for non-multibinding secondary interfaces`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())

        registrar.registerInstanceWithKoin(FirstFooImpl(), FirstFooImpl::class, listOf(FooContract::class))

        assertFailsWith<DependencyInjectionException> {
            registrar.registerInstanceWithKoin(SecondFooImpl(), SecondFooImpl::class, listOf(FooContract::class))
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
