package io.github.darkryh.katalyst.di.internal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBindingRegistrarEmptyOptionalSeverityTest {
    private lateinit var engine: TestBeanEngine
    private lateinit var scannerLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()

        scannerLogger = LoggerFactory.getLogger(ReflectionsTypeScanner::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        scannerLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        scannerLogger.detachAppender(appender)
        engine.stop()
    }

    @Test
    fun `optional KtorModule empty discovery logs INFO not WARN`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, arrayOf("io.github.darkryh.katalyst.no_such_package"))
        registrar.discoverConcreteTypes(KtorModule::class.java)

        assertTrue(
            appender.list.any { it.level == Level.INFO && it.formattedMessage.contains("No KtorModule implementations") }
        )
        assertFalse(
            appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("No KtorModule implementations") }
        )
    }

    @Test
    fun `optional KatalystMigration empty discovery logs INFO not WARN`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, arrayOf("io.github.darkryh.katalyst.no_such_package"))
        registrar.discoverConcreteTypes(KatalystMigration::class.java)

        assertTrue(
            appender.list.any { it.level == Level.INFO && it.formattedMessage.contains("No KatalystMigration implementations") }
        )
        assertFalse(
            appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("No KatalystMigration implementations") }
        )
    }
}
