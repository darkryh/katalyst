package io.github.darkryh.katalyst.di.lifecycle

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The lifecycle runners discover hooks from two places: a process-wide registry and the active
 * container. Both runners used to wrap the container half in
 * `runCatching { container.getAll<...>() }.getOrElse { emptyList() }` with no `onFailure`, so a
 * container-side failure silently shrank the hook set to whatever the registry happened to hold.
 *
 * That is the #31 blast radius reopened as a swallow: the scheduler reaches [ReadyHookRunner]
 * through the container, so one dropped enumeration can take every scheduled job with it while the
 * application logs a cheerful "lifecycle completed". These tests pin that such a failure is now
 * always reported at WARN, naming the hook type and the reason, and that the registry half still
 * runs.
 */
class HookRunnerContainerFailureLoggingTest {

    private lateinit var engine: TestBeanEngine
    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var readyLogger: Logger
    private lateinit var startupLogger: Logger
    private lateinit var readyAppender: ListAppender<ILoggingEvent>
    private lateinit var startupAppender: ListAppender<ILoggingEvent>

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        engine = TestBeanEngine()
        // The engine must be started before beans go into it, exactly as production bootstraps
        // the container before wiring: KatalystBeanEngineContract pins that for every engine.
        engine.start(emptyList(), allowOverrides = true)

        // StartupHookRunner always builds a StartupValidator from the container, and that validator
        // runs a real transaction. An in-memory H2 keeps the validator green so the assertion below
        // is about the enumeration warning and nothing else.
        databaseFactory = DatabaseFactory.create(inMemoryDatabaseConfig())
        engine.registerInstance(
            DatabaseTransactionManager(databaseFactory.database),
            DatabaseTransactionManager::class,
        )

        readyLogger = LoggerFactory.getLogger("ReadyHookRunner") as Logger
        startupLogger = LoggerFactory.getLogger("StartupHookRunner") as Logger
        readyAppender = ListAppender<ILoggingEvent>().apply { start() }
        startupAppender = ListAppender<ILoggingEvent>().apply { start() }
        readyLogger.addAppender(readyAppender)
        startupLogger.addAppender(startupAppender)
    }

    @AfterTest
    fun tearDown() {
        readyLogger.detachAppender(readyAppender)
        startupLogger.detachAppender(startupAppender)
        readyAppender.stop()
        startupAppender.stop()
        RegistryManager.resetAll()
        engine.stop()
        databaseFactory.close()
    }

    @Test
    fun `a container failure while enumerating ready hooks is reported at WARN`() = runBlocking {
        val probe = HookProbe()
        ReadyHookRegistry.register(RegistryReadyHook(probe))

        val container = FailingGetAllContainer(
            delegate = engine.container,
            failFor = ReadyHook::class,
            failure = IllegalStateException("container registry is torn down"),
        )

        ReadyHookRunner(container).invokeAll()

        val warning = assertNotNull(
            readyAppender.list.singleOrNull { it.level == Level.WARN },
            "a container-side failure while enumerating ReadyHook beans must be reported at WARN, " +
                "got: ${readyAppender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            warning.formattedMessage.contains("ReadyHook"),
            "the warning must name the hook type that could not be enumerated: ${warning.formattedMessage}",
        )
        assertTrue(
            warning.formattedMessage.contains("container registry is torn down"),
            "the warning must carry the underlying reason: ${warning.formattedMessage}",
        )
        assertTrue(
            probe.executed,
            "hooks held by the registry must still run when the container half fails",
        )
    }

    @Test
    fun `a container failure while enumerating startup hooks is reported at WARN`() = runBlocking {
        val probe = HookProbe()
        StartupHookRegistry.register(RegistryStartupHook(probe))

        val container = FailingGetAllContainer(
            delegate = engine.container,
            failFor = StartupHook::class,
            failure = IllegalStateException("container registry is torn down"),
        )

        StartupHookRunner(container).invokeAll()

        val warning = assertNotNull(
            startupAppender.list.singleOrNull { it.level == Level.WARN },
            "a container-side failure while enumerating StartupHook beans must be reported at WARN, " +
                "got: ${startupAppender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            warning.formattedMessage.contains("StartupHook"),
            "the warning must name the hook type that could not be enumerated: ${warning.formattedMessage}",
        )
        assertTrue(
            warning.formattedMessage.contains("container registry is torn down"),
            "the warning must carry the underlying reason: ${warning.formattedMessage}",
        )
        assertTrue(
            probe.executed,
            "hooks held by the registry must still run when the container half fails",
        )
    }

    @Test
    fun `a healthy enumeration produces no warning`() = runBlocking {
        ReadyHookRegistry.register(RegistryReadyHook(HookProbe()))

        ReadyHookRunner(engine.container).invokeAll()

        assertTrue(
            readyAppender.list.none { it.level == Level.WARN },
            "a normal run must stay quiet: ${readyAppender.list.map { it.formattedMessage }}",
        )
    }

    /** Delegates everything except [KatalystContainer.getAll] of one type, which always fails. */
    private class FailingGetAllContainer(
        private val delegate: KatalystContainer,
        private val failFor: KClass<*>,
        private val failure: Throwable,
    ) : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T = delegate.get(type, qualifier)
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = delegate.getOrNull(type, qualifier)
        override fun <T : Any> getAll(type: KClass<T>): List<T> {
            if (type == failFor) throw failure
            return delegate.getAll(type)
        }
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = delegate.contains(type, qualifier)
    }
}

private class HookProbe {
    var executed: Boolean = false
}

private class RegistryReadyHook(private val probe: HookProbe) : ReadyHook {
    override val id: String = "RegistryReadyHook"
    override val order: Int = 0
    override suspend fun onReady() {
        probe.executed = true
    }
}

private class RegistryStartupHook(private val probe: HookProbe) : StartupHook {
    override val id: String = "RegistryStartupHook"
    override val order: Int = 0
    override suspend fun onStartup() {
        probe.executed = true
    }
}
