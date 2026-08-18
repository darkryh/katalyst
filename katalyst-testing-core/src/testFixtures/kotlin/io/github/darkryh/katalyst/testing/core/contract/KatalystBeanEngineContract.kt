package io.github.darkryh.katalyst.testing.core.contract

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.ktor.server.application.Application
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException
import io.github.darkryh.katalyst.core.exception.DependencyInjectionException

/**
 * The behavioural contract every [KatalystBeanEngine] implementation must satisfy.
 *
 * ## Why this exists
 *
 * Katalyst ships two engines: `KoinBeanEngine`, which every application boots, and the in-memory
 * engine `katalystTestEnvironment` boots for every test. Issue #31 is what happens when they
 * disagree — a scheduler whose jobs never ran in production while the entire suite was green,
 * because the fake did not reproduce the index-key eviction that killed them. A test engine is
 * only evidence about production while it answers every observable question the same way.
 *
 * So this suite is stated once and run against **every** implementation. Subclass it in the module
 * that owns an engine, list the engines in [engines], and the whole contract runs against each.
 * An invariant asserted against one implementation is not a contract.
 *
 * ## What it pins
 *
 * Lifecycle (`start`/`stop`/`currentOrNull`), lookup (`get`/`getOrNull`/`getAll`/`contains`),
 * displacement (who wins an index key and who survives in `getAll`), ordering, shutdown close
 * semantics, and thread safety. Each invariant is stated over several marker types, because a
 * marker is exactly where two beans collide: `KatalystMigration`, `StartupHook`, `ReadyHook`,
 * `EventHandler` (qualified, the shape the event bus registers) and `KtorModule`.
 */
abstract class KatalystBeanEngineContract {

    // ------------------------------------------------------------------ harness

    /** The engines under test: a display name and a factory producing a fresh, unstarted engine. */
    protected abstract fun engines(): List<Pair<String, () -> KatalystBeanEngine>>

    /**
     * Clears process-global state between engines. `KoinBeanEngine` is an `object` backed by Koin's
     * `GlobalContext`, so a leftover context from the previous engine changes the next one's
     * behaviour; overriding subclasses must call `super`.
     */
    protected open fun resetGlobalState() {
        KatalystContainerProvider.reset()
    }

    @AfterTest
    fun contractTearDown() {
        resetGlobalState()
    }

    /** Runs [case] once per engine, each freshly started with an empty module list. */
    protected fun withEachEngine(case: (name: String, engine: KatalystBeanEngine, container: KatalystContainer) -> Unit) {
        withEachUnstartedEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)
            case(name, engine, container)
        }
    }

    /** Runs [case] once per engine, before `start` is called. */
    protected fun withEachUnstartedEngine(case: (name: String, engine: KatalystBeanEngine) -> Unit) {
        engines().forEach { (name, factory) ->
            resetGlobalState()
            val engine = factory()
            try {
                case(name, engine)
            } catch (error: AssertionError) {
                throw AssertionError("[$name] ${error.message}", error)
            } finally {
                runCatching { engine.stop() }
                resetGlobalState()
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun `currentOrNull is null before start`() = withEachUnstartedEngine { name, engine ->
        assertNull(
            engine.currentOrNull(),
            "$name: an engine that was never started has no container. Bootstrap branches on this " +
                "(DIConfiguration: currentOrNull() ?: start(modules)), so an engine that always " +
                "answers non-null silently takes the hot-reload path production never takes on a " +
                "cold boot",
        )
    }

    @Test
    fun `currentOrNull returns the started container`() = withEachEngine { name, engine, container ->
        assertSame(container, engine.currentOrNull(), "$name: currentOrNull must be the live container")
    }

    @Test
    fun `currentOrNull is null after stop`() = withEachEngine { name, engine, _ ->
        engine.stop()
        assertNull(engine.currentOrNull(), "$name: a stopped engine has no container")
    }

    @Test
    fun `registerInstance before start is rejected`() = withEachUnstartedEngine { name, engine ->
        assertFailsWith<IllegalStateException>(
            "$name: writing a bean into a container nobody started is a wiring bug and must fail loudly",
        ) {
            engine.registerInstance(MigrationA(), MigrationA::class, emptyList(), null)
        }
    }

    @Test
    fun `loadModules before start is rejected`() = withEachUnstartedEngine { name, engine ->
        assertFailsWith<IllegalStateException>(
            "$name: loading modules into a container nobody started is a wiring bug",
        ) {
            engine.loadModules(
                listOf(katalystBeanModule { single<KatalystMigration> { MigrationA() } }),
                allowOverrides = true,
            )
        }
    }

    @Test
    fun `a second start keeps the beans the first one registered`() = withEachEngine { name, engine, _ ->
        engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)

        // The hot-reload path: bootstrap runs again against a container that is already up.
        val reloaded = engine.start(emptyList(), allowOverrides = true)

        assertEquals(
            listOf("20240101_a"),
            reloaded.getAll(KatalystMigration::class).map { it.id },
            "$name: starting an already-started engine must not discard the container",
        )
    }

    @Test
    fun `stop is idempotent`() = withEachEngine { name, engine, _ ->
        engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)
        engine.stop()
        engine.stop()
        assertNull(engine.currentOrNull(), "$name: repeated stop must stay stopped, not resurrect a container")
    }

    // ------------------------------------------------------------------ lookup

    @Test
    fun `an instance is reachable by its primary type`() = withEachEngine { name, engine, container ->
        val migration = MigrationA()
        engine.registerInstance(migration, MigrationA::class, emptyList(), null)

        assertSame(migration, container.get(MigrationA::class, null), "$name: primary type lookup")
    }

    @Test
    fun `an instance is reachable by a secondary type`() = withEachEngine { name, engine, container ->
        val migration = MigrationA()
        engine.registerInstance(migration, MigrationA::class, listOf(KatalystMigration::class), null)

        assertSame(migration, container.get(KatalystMigration::class, null), "$name: secondary type lookup")
        assertEquals(
            listOf<KatalystMigration>(migration),
            container.getAll(KatalystMigration::class),
            "$name: a secondary type must also be reachable through getAll",
        )
    }

    @Test
    fun `an instance bound only to a marker stays reachable by its own class`() =
        withEachEngine { name, engine, container ->
            // `single<KatalystMigration> { ... }` in a feature bean module: the marker is the only
            // declared type. Its concrete class is the private index key that keeps it alive.
            val migration = MigrationA()
            engine.registerInstance(migration, KatalystMigration::class, emptyList(), null)

            assertSame(
                migration,
                container.get(MigrationA::class, null),
                "$name: a marker-only registration must still be reachable by its own class",
            )
        }

    @Test
    fun `getAll returns every instance sharing a marker`() = withEachEngine { name, engine, container ->
        engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)
        engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), null)
        engine.registerInstance(MigrationC(), KatalystMigration::class, emptyList(), null)

        assertEquals(
            listOf("20240101_a", "20240102_b", "20240103_c"),
            container.getAll(KatalystMigration::class).map { it.id }.sorted(),
            "$name: getAll must be complete",
        )
    }

    @Test
    fun `getAll returns one instance bound under several types exactly once`() =
        withEachEngine { name, engine, container ->
            val migration = MigrationA()
            engine.registerInstance(migration, MigrationA::class, listOf(KatalystMigration::class), null)
            // Rebinding the same instance under another type is routine during boot (the database
            // factory does it). It is still one bean.
            engine.registerInstance(migration, KatalystMigration::class, emptyList(), null)

            assertEquals(
                1,
                container.getAll(KatalystMigration::class).size,
                "$name: getAll must de-duplicate by instance, got " +
                    "${container.getAll(KatalystMigration::class).map { it.id }}",
            )
        }

    @Test
    fun `getAll of an unregistered type is empty`() = withEachEngine { name, _, container ->
        assertEquals(
            emptyList(),
            container.getAll(KatalystMigration::class),
            "$name: an unregistered type yields an empty list, not an error",
        )
    }

    @Test
    fun `get of an unregistered type throws BeanNotFoundException`() = withEachEngine { name, _, container ->
        // Same framework-owned type on every engine. `KatalystContainer` is the engine-agnostic
        // facade; a caller must not have to know which engine is underneath to catch a missing
        // bean, and must not need that engine on its own compile classpath to name the exception.
        val error = assertFailsWith<BeanNotFoundException>(
            "$name: a missing bean must raise BeanNotFoundException",
        ) {
            container.get(MigrationA::class, null)
        }

        // The structured fields are the point: a caller reacts to what was missing without
        // parsing the message.
        assertEquals(MigrationA::class, error.requestedType, "$name: requestedType must be carried")
        assertNull(error.qualifier, "$name: an unqualified lookup carries no qualifier")
    }

    @Test
    fun `a missing qualified bean carries its qualifier`() = withEachEngine { name, engine, container ->
        val error = assertFailsWith<BeanNotFoundException>("$name: qualified miss must raise") {
            container.get(MigrationA::class, "absent-qualifier")
        }

        assertEquals(MigrationA::class, error.requestedType, "$name: requestedType must be carried")
        assertEquals("absent-qualifier", error.qualifier, "$name: qualifier must be carried")
    }

    @Test
    fun `BeanNotFoundException is catchable as a dependency-injection failure`() =
        withEachEngine { name, _, container ->
            // The reason it extends DependencyInjectionException: a bootstrap can catch every DI
            // problem in one clause and report it, without enumerating subtypes.
            assertFailsWith<DependencyInjectionException>(
                "$name: a missing bean must also be a DependencyInjectionException",
            ) {
                container.get(MigrationA::class, null)
            }
        }

    @Test
    fun `getOrNull of an unregistered type is null`() = withEachEngine { name, _, container ->
        assertNull(container.getOrNull(MigrationA::class, null), "$name: getOrNull must not throw")
    }

    @Test
    fun `contains agrees with getOrNull`() = withEachEngine { name, engine, container ->
        engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)
        engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), "qualified")

        val probes: List<Pair<KClass<*>, String?>> = listOf(
            MigrationA::class to null,
            MigrationA::class to "qualified",
            MigrationB::class to null,
            MigrationB::class to "qualified",
            MigrationC::class to null,
            KatalystMigration::class to null,
            KatalystMigration::class to "qualified",
            KatalystMigration::class to "absent",
        )

        probes.forEach { (type, qualifier) ->
            assertEquals(
                container.getOrNull(type, qualifier) != null,
                container.contains(type, qualifier),
                "$name: contains(${type.simpleName}, $qualifier) must agree with getOrNull",
            )
        }
    }

    @Test
    fun `a qualified registration is reachable by its qualifier only`() =
        withEachEngine { name, engine, container ->
            val migration = MigrationA()
            engine.registerInstance(migration, MigrationA::class, listOf(KatalystMigration::class), "primary")

            assertSame(migration, container.get(MigrationA::class, "primary"), "$name: qualified lookup")
            assertNull(
                container.getOrNull(MigrationA::class, null),
                "$name: a qualified registration must not answer the unqualified key",
            )
        }

    @Test
    fun `a qualified registration still appears in its marker's getAll`() =
        withEachEngine { name, engine, container ->
            // How the event bus binds handlers, and how KatalystTestEnvironment binds event probes:
            // qualified, so they never collide, but they must all be discoverable through getAll.
            engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), "one")
            engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), "two")

            assertEquals(
                listOf("20240101_a", "20240102_b"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: a qualifier must not hide a bean from getAll",
            )
        }

    // ------------------------------------------------------------------ displacement

    @Test
    fun `get after an override returns the later writer and the displaced bean stays in getAll`() =
        withEachEngine { name, engine, container ->
            engine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)
            engine.registerInstance(MigrationB(), KatalystMigration::class, emptyList(), null)

            assertEquals(
                "20240102_b",
                container.get(KatalystMigration::class, null).id,
                "$name: the last writer owns the index key",
            )
            assertEquals(
                listOf("20240101_a", "20240102_b"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: an override of a multibinding marker must not orphan the bean it displaced",
            )
        }

    @Test
    fun `a marker-only definition survives a later registration binding that marker`() =
        withEachEngine { name, engine, container ->
            // Issue #31 exactly: `single<ReadyHook> { SchedulerInitializer() }` followed by an
            // application component that also binds ReadyHook.
            engine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)
            engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), null)

            assertEquals(
                listOf("20240101_a", "20240102_b"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: a marker-only definition must not be evicted by a later binder of that marker",
            )
        }

    @Test
    fun `registration order does not decide who survives`() {
        val results = mutableMapOf<String, List<String>>()

        withEachEngine { name, engine, container ->
            engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), null)
            engine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)
            results["$name:marker-last"] = container.getAll(KatalystMigration::class).map { it.id }.sorted()
        }
        withEachEngine { name, engine, container ->
            engine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)
            engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), null)
            results["$name:marker-first"] = container.getAll(KatalystMigration::class).map { it.id }.sorted()
        }

        assertEquals(
            1,
            results.values.distinct().size,
            "registration order must not decide who survives, got $results",
        )
        assertEquals(listOf("20240101_a", "20240102_b"), results.values.distinct().single())
    }

    @Test
    fun `two instances of the same class bound to one marker both survive`() =
        withEachEngine { name, engine, container ->
            // The residual #31 shape. Both registrations declare the same types, so the second
            // takes every index key the first held - including its concrete-class key, which was
            // the private key that was supposed to keep it reachable. `BeanDefinition` equality
            // ignores the instance, so nothing even reports it. The first bean disappears and its
            // migration silently never runs.
            //
            // Data-driven extension beans are exactly this shape: `SqlMigration(id, sql)` read from
            // a script directory, one instance per file, all of class SqlMigration.
            val first = SameClassMigration("first")
            val second = SameClassMigration("second")

            engine.registerInstance(first, KatalystMigration::class, emptyList(), null)
            engine.registerInstance(second, KatalystMigration::class, emptyList(), null)

            assertEquals(
                listOf("same-first", "same-second"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: two beans of one class sharing a marker must both stay in getAll",
            )
        }

    @Test
    fun `two instances of the same class bound to one marker both survive the registrar binding`() =
        withEachEngine { name, engine, container ->
            // Same defect through the other registration shape: concrete primary type plus the
            // marker as a secondary type, which is how AutoBindingRegistrar binds scanned classes.
            engine.registerInstance(
                SameClassMigration("first"),
                SameClassMigration::class,
                listOf(KatalystMigration::class),
                null,
            )
            engine.registerInstance(
                SameClassMigration("second"),
                SameClassMigration::class,
                listOf(KatalystMigration::class),
                null,
            )

            assertEquals(
                listOf("same-first", "same-second"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: two beans of one class sharing a marker must both stay in getAll",
            )
        }

    @Test
    fun `many instances of the same class bound to one marker all survive`() =
        withEachEngine { name, engine, container ->
            val ids = (1..10).map { "batch-$it" }
            ids.forEach { id ->
                engine.registerInstance(SameClassMigration(id), KatalystMigration::class, emptyList(), null)
            }

            assertEquals(
                ids.map { "same-$it" }.sorted(),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: every instance must survive, not just the last",
            )
        }

    // ------------------------------------------------------------------ ordering

    @Test
    fun `getAll returns instances in registration order`() = withEachEngine { name, engine, container ->
        // Every consumer of a multibinding marker sorts by a declared order and Kotlin's sort is
        // stable, so ties fall back to this order. Arbitrary here means a hook set that runs in a
        // different order in production than it did in the suite that signed it off.
        val ids = (1..12).map { "ordered-$it" }
        ids.forEach { id ->
            engine.registerInstance(SameClassMigration(id), KatalystMigration::class, emptyList(), null)
        }

        assertEquals(
            ids.map { "same-$it" },
            container.getAll(KatalystMigration::class).map { it.id },
            "$name: getAll must be deterministic and in registration order",
        )
    }

    // ------------------------------------------------------------------ marker types

    @Test
    fun `the marker invariants hold for StartupHook`() = withEachEngine { name, engine, container ->
        engine.registerInstance(ProbeStartupHook("a"), StartupHook::class, emptyList(), null)
        engine.registerInstance(ProbeStartupHook("b"), StartupHook::class, emptyList(), null)
        engine.registerInstance(OtherStartupHook(), OtherStartupHook::class, listOf(StartupHook::class), null)

        assertEquals(
            listOf("hook-a", "hook-b", "other-startup-hook"),
            container.getAll(StartupHook::class).map { it.id }.sorted(),
            "$name: StartupHook multibinding",
        )
    }

    @Test
    fun `the marker invariants hold for ReadyHook`() = withEachEngine { name, engine, container ->
        // The #31 shape verbatim: the scheduler binds `single<ReadyHook> { SchedulerInitializer() }`
        // and an application component then binds ReadyHook of its own.
        engine.registerInstance(SchedulerLikeReadyHook(), ReadyHook::class, emptyList(), null)
        engine.registerInstance(AppReadyHook(), AppReadyHook::class, listOf(ReadyHook::class), null)
        engine.registerInstance(SchedulerLikeReadyHook(), ReadyHook::class, emptyList(), null)

        assertEquals(
            listOf("app-ready-hook", "scheduler-like", "scheduler-like"),
            container.getAll(ReadyHook::class).map { it.id }.sorted(),
            "$name: registering an application ReadyHook must not evict the scheduler's",
        )
    }

    @Test
    fun `the marker invariants hold for qualified EventHandler`() = withEachEngine { name, engine, container ->
        // How KatalystTestEnvironment binds event probes and how the bus binds scanned handlers:
        // `single<EventHandler<*>>(qualifier = "...")`, several of them, same class.
        repeat(4) { index ->
            engine.registerInstance(
                ProbeEventHandler("handler-$index"),
                EventHandler::class,
                emptyList(),
                "katalyst-test-event-probe-$index",
            )
        }

        assertEquals(
            (0..3).map { "handler-$it" },
            container.getAll(EventHandler::class).map { (it as ProbeEventHandler).label }.sorted(),
            "$name: every qualified handler must be discoverable through getAll",
        )
    }

    @Test
    fun `the marker invariants hold for unqualified EventHandler of one class`() =
        withEachEngine { name, engine, container ->
            repeat(4) { index ->
                engine.registerInstance(ProbeEventHandler("handler-$index"), EventHandler::class, emptyList(), null)
            }

            assertEquals(
                (0..3).map { "handler-$it" },
                container.getAll(EventHandler::class).map { (it as ProbeEventHandler).label }.sorted(),
                "$name: dropping the qualifier must not drop the handlers",
            )
        }

    @Test
    fun `the marker invariants hold for KtorModule`() = withEachEngine { name, engine, container ->
        engine.registerInstance(ProbeKtorModule(10), KtorModule::class, emptyList(), null)
        engine.registerInstance(ProbeKtorModule(20), KtorModule::class, emptyList(), null)
        engine.registerInstance(OtherKtorModule(), OtherKtorModule::class, listOf(KtorModule::class), null)

        assertEquals(
            listOf(0, 10, 20),
            container.getAll(KtorModule::class).map { it.order }.sorted(),
            "$name: a dropped KtorModule is a route that does not exist",
        )
    }

    // ------------------------------------------------------------------ modules

    @Test
    fun `a bean module definition lands under its declared type and its own class`() =
        withEachUnstartedEngine { name, engine ->
            val container = engine.start(
                listOf(
                    katalystBeanModule {
                        single<KatalystMigration> { MigrationA() }
                        single<KatalystMigration>(qualifier = "second") { MigrationB() }
                    },
                ),
                allowOverrides = true,
            )

            assertEquals(
                listOf("20240101_a", "20240102_b"),
                container.getAll(KatalystMigration::class).map { it.id }.sorted(),
                "$name: both module definitions must be discoverable",
            )
            assertNotNull(
                container.getOrNull(MigrationA::class, null),
                "$name: a module definition must also be reachable by its concrete class",
            )
        }

    // ------------------------------------------------------------------ shutdown

    @Test
    fun `beans close in strict reverse registration order`() = withEachEngine { name, engine, _ ->
        // The contract the original production incident turned on: DatabaseFactory is registered
        // first because everything depends on it, so it must close last.
        val closed = mutableListOf<String>()
        engine.registerInstance(FirstProbe(closed), FirstProbe::class)
        engine.registerInstance(SecondProbe(closed), SecondProbe::class)
        engine.registerInstance(ThirdProbe(closed), ThirdProbe::class)

        engine.stop()

        assertEquals(listOf("third", "second", "first"), closed, "$name: reverse registration order")
    }

    @Test
    fun `a close that throws does not strand its neighbours`() = withEachEngine { name, engine, _ ->
        val closed = mutableListOf<String>()
        val first = FirstProbe(closed)
        val second = SecondProbe(closed, failOnClose = true)
        val third = ThirdProbe(closed)
        engine.registerInstance(first, FirstProbe::class)
        engine.registerInstance(second, SecondProbe::class)
        engine.registerInstance(third, ThirdProbe::class)

        engine.stop()

        assertEquals(listOf("third", "second", "first"), closed, "$name: one failure must not strand the rest")
        assertEquals(1, first.closeCount, "$name: the bean closed after the throwing one must still close")
        assertEquals(1, third.closeCount, "$name: the bean closed before the throwing one must close")
    }

    @Test
    fun `a second stop does not close a bean twice`() = withEachEngine { name, engine, _ ->
        val closed = mutableListOf<String>()
        val probe = FirstProbe(closed)
        engine.registerInstance(probe, FirstProbe::class)

        engine.stop()
        engine.stop()

        assertEquals(1, probe.closeCount, "$name: a bean must be closed exactly once across repeated stops")
    }

    @Test
    fun `a displaced closeable is still closed at shutdown`() = withEachEngine { name, engine, _ ->
        // A bean the container created stays the container's to release even after another bean
        // took its index keys. Reading the close list off the live index would drop it.
        val closed = mutableListOf<String>()
        val displaced = FirstProbe(closed, label = "displaced")
        val winner = FirstProbe(closed, label = "winner")
        engine.registerInstance(displaced, FirstProbe::class)
        engine.registerInstance(winner, FirstProbe::class)

        engine.stop()

        assertEquals(
            listOf("winner", "displaced"),
            closed,
            "$name: a displaced bean is still the container's to close",
        )
    }

    @Test
    fun `resolution during shutdown still hands out an already-closed bean`() =
        withEachEngine { name, engine, container ->
            // CONTRACT PIN, not an assertion of desirable behaviour. Shutdown closes managed
            // instances first and tears the container down afterwards, so there is a window in
            // which get() returns a bean whose close() has already run. Rejecting resolution during
            // shutdown needs new state threaded through KatalystContainer, which is an API change.
            // If that ever changes, this is the test that has to be rewritten deliberately.
            val closed = mutableListOf<String>()
            val first = FirstProbe(closed)
            val second = SecondProbe(closed)
            engine.registerInstance(first, FirstProbe::class)
            engine.registerInstance(second, SecondProbe::class)

            var resolvedDuringShutdown: Any? = null
            var resolvedWasAlreadyClosed = false
            first.onClose = {
                resolvedDuringShutdown = container.getOrNull(SecondProbe::class, null)
                resolvedWasAlreadyClosed = (resolvedDuringShutdown as? CloseProbe)?.closeCount == 1
            }

            engine.stop()

            assertSame(second, resolvedDuringShutdown, "$name: the container still resolves during shutdown")
            assertTrue(resolvedWasAlreadyClosed, "$name: the bean handed out during shutdown is already closed")
        }

    // ------------------------------------------------------------------ concurrency

    @Test
    fun `concurrent registration loses nothing`() = withEachEngine { name, engine, container ->
        // `katalystTestEnvironment` is shipped code that consumers boot from their own suites, and
        // a parallel test runner or a component that registers from a coroutine dispatcher reaches
        // registerInstance from several threads. A plain LinkedHashMap silently drops entries under
        // that load - a bean that is simply not there, with no error anywhere.
        val threads = 8
        val perThread = 40
        val expected = threads * perThread

        val failures = ConcurrentLinkedQueue<Throwable>()
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            (0 until threads).map { thread ->
                pool.submit {
                    runCatching {
                        barrier.await()
                        (0 until perThread).forEach { slot ->
                            engine.registerInstance(
                                SameClassMigration("t$thread-$slot"),
                                SameClassMigration::class,
                                listOf(KatalystMigration::class),
                                "t$thread-$slot",
                            )
                        }
                    }.onFailure { failures += it }
                }
            }.forEach { it.get(120, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue(failures.isEmpty(), "$name: concurrent registration threw ${failures.map { it }}")
        assertEquals(
            expected,
            container.getAll(KatalystMigration::class).size,
            "$name: every concurrently registered bean must be present",
        )
        (0 until threads).forEach { thread ->
            (0 until perThread).forEach { slot ->
                assertNotNull(
                    container.getOrNull(SameClassMigration::class, "t$thread-$slot"),
                    "$name: bean t$thread-$slot was lost",
                )
            }
        }
    }

    @Test
    fun `concurrent registration and resolution do not corrupt the index`() =
        withEachEngine { name, engine, container ->
            val writers = 4
            val readers = 4
            val perWriter = 40

            val failures = ConcurrentLinkedQueue<Throwable>()
            val barrier = CyclicBarrier(writers + readers)
            val pool = Executors.newFixedThreadPool(writers + readers)
            try {
                val writing = (0 until writers).map { thread ->
                    pool.submit {
                        runCatching {
                            barrier.await()
                            (0 until perWriter).forEach { slot ->
                                engine.registerInstance(
                                    SameClassMigration("w$thread-$slot"),
                                    SameClassMigration::class,
                                    listOf(KatalystMigration::class),
                                    "w$thread-$slot",
                                )
                            }
                        }.onFailure { failures += it }
                    }
                }
                val reading = (0 until readers).map {
                    pool.submit {
                        runCatching {
                            barrier.await()
                            repeat(perWriter) {
                                container.getAll(KatalystMigration::class).forEach { migration ->
                                    check(migration.id.isNotEmpty())
                                }
                            }
                        }.onFailure { failures += it }
                    }
                }
                (writing + reading).forEach { it.get(120, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertTrue(
                failures.isEmpty(),
                "$name: concurrent read/write threw ${failures.map { "${it::class.simpleName}: ${it.message}" }}",
            )
            assertEquals(
                writers * perWriter,
                container.getAll(KatalystMigration::class).size,
                "$name: reads must not disturb the index",
            )
        }

    @Test
    fun `an engine that was stopped rejects further registration`() = withEachEngine { name, engine, _ ->
        engine.stop()
        assertFailsWith<IllegalStateException>("$name: a stopped engine has no container to register into") {
            engine.registerInstance(MigrationA(), MigrationA::class, emptyList(), null)
        }
    }

    @Test
    fun `a stopped engine reports nothing as contained`() = withEachEngine { name, engine, container ->
        engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)
        engine.stop()

        assertFalse(container.contains(MigrationA::class, null), "$name: a stopped container holds nothing")
        assertEquals(emptyList(), container.getAll(KatalystMigration::class), "$name: a stopped container is empty")
    }

    // ------------------------------------------------------------------ fixtures

    protected class MigrationA : KatalystMigration {
        override val id: String = "20240101_a"
        override fun up() = Unit
    }

    protected class MigrationB : KatalystMigration {
        override val id: String = "20240102_b"
        override fun up() = Unit
    }

    protected class MigrationC : KatalystMigration {
        override val id: String = "20240103_c"
        override fun up() = Unit
    }

    /** Several instances, one class, one marker — the residual #31 shape. */
    protected class SameClassMigration(tag: String) : KatalystMigration {
        override val id: String = "same-$tag"
        override fun up() = Unit
    }

    protected class ProbeStartupHook(tag: String) : StartupHook {
        override val id: String = "hook-$tag"
        override suspend fun onStartup() = Unit
    }

    protected class OtherStartupHook : StartupHook {
        override val id: String = "other-startup-hook"
        override suspend fun onStartup() = Unit
    }

    protected class SchedulerLikeReadyHook : ReadyHook {
        override val id: String = "scheduler-like"
        override suspend fun onReady() = Unit
    }

    protected class AppReadyHook : ReadyHook {
        override val id: String = "app-ready-hook"
        override suspend fun onReady() = Unit
    }

    protected class ProbeEvent : DomainEvent

    protected class ProbeEventHandler(val label: String) : EventHandler<ProbeEvent> {
        override val eventType: KClass<ProbeEvent> = ProbeEvent::class
        override suspend fun handle(event: ProbeEvent) = Unit
    }

    protected class ProbeKtorModule(override val order: Int) : KtorModule {
        override fun install(application: Application) = Unit
    }

    protected class OtherKtorModule : KtorModule {
        override fun install(application: Application) = Unit
    }

    protected open class CloseProbe(
        private val label: String,
        private val closed: MutableList<String>,
        private val failOnClose: Boolean = false,
    ) : AutoCloseable {
        var closeCount: Int = 0
        var onClose: (() -> Unit)? = null

        override fun close() {
            closeCount++
            closed += label
            onClose?.invoke()
            if (failOnClose) throw IllegalStateException("$label refused to close")
        }
    }

    /** Distinct classes so three probes bind distinct index keys and do not displace one another. */
    protected class FirstProbe(
        closed: MutableList<String>,
        failOnClose: Boolean = false,
        label: String = "first",
    ) : CloseProbe(label, closed, failOnClose)

    protected class SecondProbe(
        closed: MutableList<String>,
        failOnClose: Boolean = false,
        label: String = "second",
    ) : CloseProbe(label, closed, failOnClose)

    protected class ThirdProbe(
        closed: MutableList<String>,
        failOnClose: Boolean = false,
        label: String = "third",
    ) : CloseProbe(label, closed, failOnClose)
}
