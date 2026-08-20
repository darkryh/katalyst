package io.github.darkryh.katalyst.transactions.adapter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionAdapterRegistryPhaseLoggingTest {

    private class NoopAdapter(private val adapterName: String) : TransactionAdapter {
        override fun name(): String = adapterName
        override fun priority(): Int = 0
        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) = Unit
    }

    private val logger = LoggerFactory.getLogger(TransactionAdapterRegistry::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private val originalLevel = logger.level

    init {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun resetAppender() {
        appender.list.clear()
        logger.level = originalLevel
    }

    @Test
    fun `phase logging disabled suppresses phase debug logs`() = runTest {
        logger.level = Level.DEBUG
        val registry = TransactionAdapterRegistry()
        registry.register(NoopAdapter("test-adapter"))

        registry.executeAdapters(
            phase = TransactionPhase.BEFORE_COMMIT,
            context = TransactionEventContext(),
            phaseLoggingEnabled = false
        )

        assertFalse(appender.list.any { it.formattedMessage.contains("Executing 1 adapter(s) for phase") })
        assertFalse(appender.list.any { it.formattedMessage.contains("Phase execution summary") })
    }

    @Test
    fun `phase logging enabled keeps concise debug and moves adapter details to trace`() = runTest {
        logger.level = Level.DEBUG
        val registry = TransactionAdapterRegistry()
        registry.register(NoopAdapter("trace-adapter"))

        registry.executeAdapters(
            phase = TransactionPhase.AFTER_BEGIN,
            context = TransactionEventContext(),
            phaseLoggingEnabled = true
        )

        assertTrue(appender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("Executing 1 adapter(s) for phase") })
        assertTrue(appender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("Phase execution summary") })
        assertFalse(appender.list.any { it.formattedMessage.contains("executed successfully in") })

        appender.list.clear()
        logger.level = Level.TRACE

        registry.executeAdapters(
            phase = TransactionPhase.AFTER_BEGIN,
            context = TransactionEventContext(),
            phaseLoggingEnabled = true
        )

        assertTrue(appender.list.any { it.level == Level.TRACE && it.formattedMessage.contains("Executing adapter trace-adapter") })
        assertTrue(appender.list.any { it.level == Level.TRACE && it.formattedMessage.contains("executed successfully in") })
    }
}
