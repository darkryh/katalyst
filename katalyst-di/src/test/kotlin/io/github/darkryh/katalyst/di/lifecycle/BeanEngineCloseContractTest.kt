package io.github.darkryh.katalyst.di.lifecycle

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.testing.core.contract.KatalystBeanEngineContract
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bean-engine contract, run against this module's [TestBeanEngine] double and the real
 * [KoinBeanEngine].
 *
 * Seventeen test classes in this module wire their fixtures through [TestBeanEngine]. Whatever it
 * answers is what those tests are evidence about, so it has to answer what production answers -
 * the exact lesson of issue #31, where the shipped test engine did not reproduce the production
 * engine's index eviction and a green suite said nothing about an application whose scheduled jobs
 * never ran.
 *
 * **Consolidation note.** This file used to carry its own `withEachEngine` harness and its own
 * copy of the shutdown invariants, because `katalyst-testing-core` was out of scope for the change
 * that wrote it. The invariants now live once, in
 * `io.github.darkryh.katalyst.testing.core.contract.KatalystBeanEngineContract` (a test fixture of
 * `katalyst-testing-core`), and both this module and `katalyst-testing-core` subclass it. A future
 * engine has one suite to pass. What stays here is the part that needs a logback appender, which
 * the shared fixture deliberately does not depend on.
 */
class BeanEngineCloseContractTest : KatalystBeanEngineContract() {

    override fun engines(): List<Pair<String, () -> KatalystBeanEngine>> = listOf(
        "TestBeanEngine" to { TestBeanEngine() },
        "KoinBeanEngine" to { KoinBeanEngine },
    )

    override fun resetGlobalState() {
        super.resetGlobalState()
        runCatching { stopKoin() }
    }

    private fun loggerNameOf(engineName: String): String = engineName

    @Test
    fun `a close that throws is reported at WARN naming the class that refused`() =
        withEachEngine { name, engine, _ ->
            val logger = LoggerFactory.getLogger(loggerNameOf(name)) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            try {
                val closed = mutableListOf<String>()
                engine.registerInstance(FirstProbe(closed), FirstProbe::class)
                engine.registerInstance(SecondProbe(closed, failOnClose = true), SecondProbe::class)
                engine.registerInstance(ThirdProbe(closed), ThirdProbe::class)

                engine.stop()

                assertEquals(listOf("third", "second", "first"), closed, "$name: close order")
                val warning = assertNotNull(
                    appender.list.singleOrNull { it.level == Level.WARN },
                    "$name: a bean that fails to close must be reported at WARN, got " +
                        "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
                )
                assertTrue(
                    warning.formattedMessage.contains(SecondProbe::class.qualifiedName!!),
                    "$name: the warning must name the class that refused to close: " +
                        warning.formattedMessage,
                )
            } finally {
                logger.detachAppender(appender)
                appender.stop()
            }
        }
}
