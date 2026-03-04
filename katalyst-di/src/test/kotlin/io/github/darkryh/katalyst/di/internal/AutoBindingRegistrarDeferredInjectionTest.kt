package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.injection.InjectNamed
import io.github.darkryh.katalyst.di.injection.Provider
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class AutoBindingRegistrarDeferredInjectionTest {
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<DeferredFooContract>(named("beta")) { FooBeta() }
                    single<DeferredFooContract>(named("alpha")) { FooAlpha() }
                    single { RuntimeDependency("runtime") }
                }
            )
        }
        koin = GlobalContext.get()
        AutoBindingRegistrar.resetSecondaryTypeTracking()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `instantiate should inject qualified direct dependency`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())
        val instance = registrar.instantiate(NeedsQualifiedFoo::class)
        assertEquals("alpha", instance.foo.id)
    }

    @Test
    fun `instantiate should inject deferred Provider with qualifier`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())
        val instance = registrar.instantiate(NeedsProviderFoo::class)

        assertEquals("beta", instance.fooProvider.get().id)
    }

    @Test
    fun `instantiate should inject lazy dependency`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())
        val instance = registrar.instantiate(NeedsLazyRuntimeDependency::class)
        assertEquals("runtime", instance.dependency.value.label)
    }

    @Test
    fun `instantiate should inject function provider`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())
        val instance = registrar.instantiate(NeedsFunctionProvider::class)
        assertEquals("runtime", instance.provider().label)
    }

    @Test
    fun `provider should break eager constructor cycle when resolved later`() {
        val registrar = AutoBindingRegistrar(koin, emptyArray())

        val sideA = registrar.instantiate(SideAWithProvider::class)
        registrar.registerInstanceWithKoin(sideA, SideAWithProvider::class, emptyList())

        val sideB = registrar.instantiate(SideBWithDirect::class)
        assertNotNull(sideB)
        assertSame(sideA, sideB.sideA)
    }
}

private interface DeferredFooContract {
    val id: String
}

private class FooAlpha : DeferredFooContract {
    override val id: String = "alpha"
}

private class FooBeta : DeferredFooContract {
    override val id: String = "beta"
}

private class RuntimeDependency(val label: String)

private class NeedsQualifiedFoo(
    @InjectNamed("alpha") val foo: DeferredFooContract
)

private class NeedsProviderFoo(
    @InjectNamed("beta") val fooProvider: Provider<DeferredFooContract>
)

private class NeedsLazyRuntimeDependency(
    val dependency: Lazy<RuntimeDependency>
)

private class NeedsFunctionProvider(
    val provider: () -> RuntimeDependency
)

private class SideAWithProvider(
    val sideBProvider: Provider<SideBWithDirect>
)

private class SideBWithDirect(
    val sideA: SideAWithProvider
)
