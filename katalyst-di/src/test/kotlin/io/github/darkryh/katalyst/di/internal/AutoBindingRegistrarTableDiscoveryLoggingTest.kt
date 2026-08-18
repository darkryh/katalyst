package io.github.darkryh.katalyst.di.internal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Table discovery used to end in `instantiateIfPossible() ?: return@forEach`: the constructor
 * exception was already swallowed by `getOrNull()`, so the surrounding `runCatching { }.onFailure`
 * never fired and a [io.github.darkryh.katalyst.core.persistence.Table] whose constructor throws was
 * dropped with **no log at any level**. Its schema was then never created, and the application ran
 * against a table that does not exist.
 *
 * Fixtures live in `katalyst.ditest.tables` so no other test's scan can trip over them.
 */
class AutoBindingRegistrarTableDiscoveryLoggingTest {

    private lateinit var engine: TestBeanEngine
    private lateinit var registrarLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
        // The engine must be started before beans go into it, exactly as production bootstraps
        // the container before wiring: KatalystBeanEngineContract pins that for every engine.
        engine.start(emptyList(), allowOverrides = true)
        registrarLogger = LoggerFactory.getLogger("AutoBindingRegistrar") as Logger
        previousLevel = registrarLogger.level
        registrarLogger.level = Level.DEBUG
        appender = ListAppender<ILoggingEvent>().apply { start() }
        registrarLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        registrarLogger.detachAppender(appender)
        appender.stop()
        registrarLogger.level = previousLevel
        TableRegistry.reset()
        engine.stop()
    }

    @Test
    fun `a table whose constructor throws is reported at WARN instead of vanishing`() {
        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("katalyst.ditest.tables"),
        )

        registrar.registerTables()

        val warning = assertNotNull(
            appender.list.singleOrNull { it.level == Level.WARN },
            "a discovered table that cannot be instantiated must be reported at WARN, got: " +
                "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            warning.formattedMessage.contains("katalyst.ditest.tables.ExplodingTable"),
            "the warning must name the table class: ${warning.formattedMessage}",
        )
        assertTrue(
            warning.formattedMessage.contains("column 'name' is declared twice"),
            "the warning must carry the constructor's own reason, not the reflection wrapper's " +
                "null message: ${warning.formattedMessage}",
        )
        assertTrue(
            warning.formattedMessage.contains("schema"),
            "the warning must say what the consequence is: ${warning.formattedMessage}",
        )
    }

    @Test
    fun `an abstract table base stays routine at DEBUG`() {
        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("katalyst.ditest.tables"),
        )

        registrar.registerTables()

        assertTrue(
            appender.list.any {
                it.level == Level.DEBUG &&
                    it.formattedMessage.contains("katalyst.ditest.tables.AbstractBaseTable")
            },
            "a shared abstract table base is a normal declaration shape and must not warn: " +
                "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            appender.list.none {
                it.level == Level.WARN && it.formattedMessage.contains("AbstractBaseTable")
            },
            "an abstract base must not be reported as a broken table: " +
                "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
    }

    @Test
    fun `a table that cannot be built is not registered for schema creation`() {
        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("katalyst.ditest.tables"),
        )

        registrar.registerTables()

        assertTrue(
            TableRegistry.getAll().none { it.tableName == "exploding_table" },
            "the behaviour itself is unchanged: a table that cannot be built is still skipped - " +
                "what changed is that it is no longer skipped in silence",
        )
    }
}
