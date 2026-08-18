package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.migrations.KatalystMigration
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.definition.indexKey
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The production engine's own unit tests.
 *
 * `KoinBeanEngine` is what every Katalyst application boots and it is an `object`, so its
 * bookkeeping — the closeable list, the registration ledger, the cached container — is
 * process-global and survives between applications in one JVM. None of that was covered by this
 * module's suite, which tested `KoinKatalystContainer` and never instantiated the engine at all;
 * the cross-engine behaviour lives in `KatalystBeanEngineContract`, and what is asserted here is
 * the Koin-specific mechanics that contract cannot see: which index keys a registration writes,
 * what `stop` tears down and in which order, and whether a second application in the same JVM
 * inherits the first one's state.
 */
class KoinBeanEngineTest {

    class MigrationA : KatalystMigration {
        override val id: String = "20240101_a"
        override fun up() = Unit
    }

    class CountingProbe : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
        }
    }

    @BeforeTest
    fun setUp() = resetGlobalState()

    @AfterTest
    fun tearDown() = resetGlobalState()

    private fun resetGlobalState() {
        runCatching { KoinBeanEngine.stop() }
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    // ------------------------------------------------------------------ start

    @Test
    fun `start on a fresh process creates the Koin context`() {
        assertNull(GlobalContext.getOrNull(), "fixture: no Koin context yet")

        val container = KoinBeanEngine.start(
            listOf(katalystBeanModule { single<KatalystMigration> { MigrationA() } }),
            allowOverrides = true,
        )

        assertNotNull(GlobalContext.getOrNull(), "start must bootstrap Koin")
        assertEquals(
            listOf("20240101_a"),
            container.getAll(KatalystMigration::class).map { it.id },
            "modules passed to start must be registered",
        )
        assertSame(
            container,
            KatalystContainerProvider.currentOrNull(),
            "start must publish its container to the provider",
        )
    }

    @Test
    fun `start against an existing Koin context reuses it and keeps its definitions`() {
        // The hot-reload path, and the path an application that already runs Koin takes.
        val existing = startKoin { modules(module { single { PreExistingBean("koin-owned") } }) }.koin

        val container = KoinBeanEngine.start(
            listOf(katalystBeanModule { single<KatalystMigration> { MigrationA() } }),
            allowOverrides = true,
        )

        assertSame(
            existing,
            (container as KoinKatalystContainer).koin,
            "an existing Koin context must be reused, not replaced",
        )
        assertEquals(
            "koin-owned",
            container.get(PreExistingBean::class, null).id,
            "definitions the caller already loaded must survive",
        )
        assertEquals(
            listOf("20240101_a"),
            container.getAll(KatalystMigration::class).map { it.id },
            "Katalyst definitions must be added to the existing context",
        )
    }

    @Test
    fun `start twice hands out the same container`() {
        val first = KoinBeanEngine.start(emptyList(), allowOverrides = true)
        val second = KoinBeanEngine.start(emptyList(), allowOverrides = true)

        assertSame(first, second, "one live Koin must be represented by one container")
        assertSame(first, KoinBeanEngine.currentOrNull(), "currentOrNull must be that same container")
    }

    // ------------------------------------------------------------------ loadModules

    @Test
    fun `loadModules before start is rejected`() {
        val error = assertFailsWith<IllegalStateException> {
            KoinBeanEngine.loadModules(
                listOf(katalystBeanModule { single<KatalystMigration> { MigrationA() } }),
                allowOverrides = true,
            )
        }
        assertTrue(
            error.message.orEmpty().contains("not initialized"),
            "the message must say the engine was never bootstrapped: ${error.message}",
        )
    }

    @Test
    fun `loadModules after start adds to the live context`() {
        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true)

        KoinBeanEngine.loadModules(
            listOf(katalystBeanModule { single<KatalystMigration> { MigrationA() } }),
            allowOverrides = true,
        )

        assertEquals(listOf("20240101_a"), container.getAll(KatalystMigration::class).map { it.id })
    }

    // ------------------------------------------------------------------ index keys

    @OptIn(KoinInternalApi::class)
    @Test
    fun `registerInstance writes the declared keys plus one private key`() {
        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true) as KoinKatalystContainer
        val koin = container.koin
        val scope = koin.scopeRegistry.rootScope.scopeQualifier
        val before = koin.instanceRegistry.instances.keys.toSet()

        val migration = MigrationA()
        KoinBeanEngine.registerInstance(migration, MigrationA::class, listOf(KatalystMigration::class), null)

        val written = koin.instanceRegistry.instances.keys - before
        val declared = setOf(
            indexKey(MigrationA::class, null, scope),
            indexKey(KatalystMigration::class, null, scope),
        )
        assertTrue(declared.all { it in written }, "declared types must be indexed; wrote $written")

        val private = written - declared
        assertEquals(1, private.size, "exactly one private key per registration; wrote $written")
        assertTrue(
            private.single().startsWith("katalyst-registration#"),
            "the private key must be unmistakably Katalyst's: ${private.single()}",
        )

        val factories = written.map { koin.instanceRegistry.instances[it] }
        assertEquals(1, factories.distinct().size, "every key must point at the one factory")
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun `a marker-only registration is also indexed under its own class`() {
        // `single<KatalystMigration> { ... }`: the marker is the only declared type, so the
        // concrete class is the key that keeps it addressable. This is the #31 fix.
        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true) as KoinKatalystContainer
        val koin = container.koin
        val scope = koin.scopeRegistry.rootScope.scopeQualifier

        KoinBeanEngine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)

        assertTrue(
            indexKey(MigrationA::class, null, scope) in koin.instanceRegistry.instances,
            "the instance's own class must be indexed",
        )
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun `two registrations of one class never share a private key`() {
        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true) as KoinKatalystContainer
        val koin = container.koin

        repeat(5) { KoinBeanEngine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null) }

        val privateKeys = koin.instanceRegistry.instances.keys.filter { it.startsWith("katalyst-registration#") }
        assertEquals(5, privateKeys.size, "each registration keeps a key of its own, got $privateKeys")
        assertEquals(
            5,
            privateKeys.mapNotNull { koin.instanceRegistry.instances[it] }.distinct().size,
            "no two registrations may share a private key",
        )
    }

    @Test
    fun `registerInstance before start is rejected and remembers nothing`() {
        val probe = CountingProbe()

        assertFailsWith<IllegalStateException> {
            KoinBeanEngine.registerInstance(probe, CountingProbe::class, emptyList(), null)
        }

        // The rejected instance must not have been queued for a shutdown that will never come:
        // a later, legitimate boot would otherwise close a bean it never created.
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        KoinBeanEngine.stop()
        assertEquals(0, probe.closeCount, "a rejected registration must not be tracked for close")
    }

    // ------------------------------------------------------------------ stop

    @Test
    fun `stop closes beans first, then stops Koin, then clears the provider`() {
        KoinBeanEngine.start(emptyList(), allowOverrides = true)

        var koinWasUpDuringClose = false
        var providerWasSetDuringClose = false
        val probe = object : AutoCloseable {
            override fun close() {
                koinWasUpDuringClose = GlobalContext.getOrNull() != null
                providerWasSetDuringClose = KatalystContainerProvider.currentOrNull() != null
            }
        }
        KoinBeanEngine.registerInstance(probe, AutoCloseable::class, emptyList(), null)

        KoinBeanEngine.stop()

        assertTrue(koinWasUpDuringClose, "a bean must still be able to reach the container while closing")
        assertTrue(providerWasSetDuringClose, "the provider must still be set while beans close")
        assertNull(GlobalContext.getOrNull(), "stop must tear the Koin context down")
        assertNull(KatalystContainerProvider.currentOrNull(), "stop must clear the container provider")
        assertNull(KoinBeanEngine.currentOrNull(), "a stopped engine has no container")
    }

    @Test
    fun `a second stop is a no-op`() {
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        val probe = CountingProbe()
        KoinBeanEngine.registerInstance(probe, CountingProbe::class, emptyList(), null)

        KoinBeanEngine.stop()
        KoinBeanEngine.stop()

        assertEquals(1, probe.closeCount, "a bean must be closed exactly once across repeated stops")
        assertNull(KoinBeanEngine.currentOrNull())
    }

    @Test
    fun `stop on an engine that was never started does not throw`() {
        KoinBeanEngine.stop()
        assertNull(KoinBeanEngine.currentOrNull())
    }

    // ------------------------------------------------------------------ process-global state

    @Test
    fun `managed instances do not leak across start-stop cycles`() {
        // The engine is an `object`: the closeable list, the container cache and the registration
        // ledger outlive any one application. An entry left behind by the previous cycle is a bean
        // closed twice - or, on the second boot, a `close()` on an object the new container never
        // handed out.
        val first = CountingProbe()
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        KoinBeanEngine.registerInstance(first, CountingProbe::class, emptyList(), null)
        KoinBeanEngine.stop()
        assertEquals(1, first.closeCount, "cycle 1 must close its own bean")

        val second = CountingProbe()
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        KoinBeanEngine.registerInstance(second, CountingProbe::class, emptyList(), null)
        KoinBeanEngine.stop()

        assertEquals(1, first.closeCount, "cycle 2 must not re-close cycle 1's bean")
        assertEquals(1, second.closeCount, "cycle 2 must close its own bean")
    }

    @Test
    fun `the registration ledger does not leak across start-stop cycles`() {
        val leaked = MigrationA()
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        KoinBeanEngine.registerInstance(leaked, KatalystMigration::class, emptyList(), null)
        KoinBeanEngine.stop()

        assertEquals(
            Long.MAX_VALUE,
            KoinRegistrationOrder.sequenceOf(leaked),
            "stop must clear the registration ledger; otherwise every application ever booted in " +
                "this JVM stays reachable from it",
        )
    }

    @Test
    fun `a second boot orders getAll from its own registrations`() {
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
        repeat(3) { KoinBeanEngine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null) }
        KoinBeanEngine.stop()

        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true)
        val second = List(3) { MigrationA() }
        second.forEach { KoinBeanEngine.registerInstance(it, KatalystMigration::class, emptyList(), null) }

        assertEquals(
            second,
            container.getAll(KatalystMigration::class),
            "the second boot must see exactly its own beans, in its own registration order",
        )
    }

    private data class PreExistingBean(val id: String)
}
