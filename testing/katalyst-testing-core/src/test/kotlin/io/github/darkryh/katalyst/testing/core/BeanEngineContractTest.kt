package io.github.darkryh.katalyst.testing.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.testing.core.contract.KatalystBeanEngineContract
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bean-engine contract, run against the two engines Katalyst actually ships.
 *
 * `KoinBeanEngine` is what every application boots; `TestKatalystBeanEngine` is what
 * `katalystTestEnvironment` boots for every test, including in consumers' own suites. Issue #31
 * is the record of what a divergence between them costs: the scheduler's `ReadyHook` was evicted
 * from the Koin registry in production while the in-memory engine kept it, so no scheduled job ran
 * and the entire framework suite was green.
 *
 * Every invariant lives in [KatalystBeanEngineContract] so a third engine has one suite to pass.
 * What is added here is the part that needs a log appender: displacement is only useful if it is
 * *reported*, and a fake that stays silent about an eviction is precisely how #31 hid.
 */
class BeanEngineContractTest : KatalystBeanEngineContract() {

    override fun engines(): List<Pair<String, () -> KatalystBeanEngine>> = listOf(
        "TestKatalystBeanEngine" to { TestKatalystBeanEngine() },
        "KoinBeanEngine" to { KoinBeanEngine },
    )

    override fun resetGlobalState() {
        super.resetGlobalState()
        runCatching { stopKoin() }
    }

    /** The logger each engine reports index displacement through. */
    private fun loggerNameOf(engineName: String): String =
        if (engineName == "KoinBeanEngine") "KoinBeanEngine" else "TestKatalystBeanEngine"

    private fun <T> capturingLogs(engineName: String, block: (ListAppender<ILoggingEvent>) -> T): T {
        val logger = LoggerFactory.getLogger(loggerNameOf(engineName)) as Logger
        val previousLevel = logger.level
        logger.level = Level.DEBUG
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        return try {
            block(appender)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            logger.level = previousLevel
        }
    }

    @Test
    fun `rebinding an index key is reported`() = withEachEngine { name, engine, _ ->
        capturingLogs(name) { appender ->
            engine.registerInstance(MigrationA(), KatalystMigration::class, emptyList(), null)
            engine.registerInstance(MigrationB(), KatalystMigration::class, emptyList(), null)

            val messages = appender.list.map { "${it.level}: ${it.formattedMessage}" }
            assertTrue(
                appender.list.any { it.formattedMessage.contains("KatalystMigration") },
                "$name: taking an index key from another definition must leave a trace, got $messages",
            )
        }
    }

    @Test
    fun `a routine rebind is not reported at WARN`() = withEachEngine { name, engine, _ ->
        capturingLogs(name) { appender ->
            // Several beans of one class sharing a marker is a legitimate, common shape (one
            // `SqlMigration` per script file). Every one of them displaces the previous holder of
            // the marker key, and every one of them survives in getAll, so none of it is a warning.
            repeat(6) { index ->
                engine.registerInstance(SameClassMigration("m$index"), KatalystMigration::class, emptyList(), null)
            }

            val warnings = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            assertEquals(
                emptyList(),
                warnings,
                "$name: a displacement that orphans nobody must not be a warning",
            )
        }
    }

    @Test
    fun `a bean that refuses to close is reported at WARN naming its class`() =
        withEachEngine { name, engine, _ ->
            capturingLogs(name) { appender ->
                val closed = mutableListOf<String>()
                engine.registerInstance(FirstProbe(closed), FirstProbe::class)
                engine.registerInstance(SecondProbe(closed, failOnClose = true), SecondProbe::class)
                engine.registerInstance(ThirdProbe(closed), ThirdProbe::class)

                engine.stop()

                val warning = appender.list.singleOrNull { it.level == Level.WARN }
                assertTrue(
                    warning != null && warning.formattedMessage.contains("SecondProbe"),
                    "$name: a bean that fails to close must be reported at WARN naming the class, got " +
                        "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
                )
            }
        }

    @Test
    fun `the test engine is the engine katalystTestEnvironment boots`() {
        // A contract only helps while the shipped test path actually runs the engine it pins.
        katalystTestEnvironment().use { environment ->
            assertEquals(
                "katalyst-test",
                environment.options.beanEngine?.id,
                "katalystTestEnvironment must boot the engine this contract pins",
            )
        }
        KatalystContainerProvider.reset()
    }
}
