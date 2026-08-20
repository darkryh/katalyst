package io.github.darkryh.katalyst.migrations.feature

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

/**
 * The migrations feature must never fail silently.
 *
 * Issue #16 was invisible in production for exactly one reason: "enabled, running at startup,
 * found nothing to run" was logged at INFO, so a boot that performed no schema management at
 * all read the same as a healthy one. Whatever else changes, that condition has to be
 * *visible*.
 */
class MigrationFeatureSilentNoOpTest {

    private lateinit var featureLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeTest
    fun setUp() {
        featureLogger = LoggerFactory.getLogger(MigrationFeature::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        featureLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        featureLogger.detachAppender(appender)
    }

    /** A container with nothing registered, standing in for "no migrations discovered". */
    private class EmptyContainer : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            error("No bean registered for ${type.qualifiedName}")

        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }

    @Test
    fun `enabled with runAtStartup but no migrations discovered warns`() {
        val feature = MigrationFeature(MigrationOptions(runAtStartup = true))

        feature.onReady(KatalystBeanContext(EmptyContainer()))

        assertTrue(
            appender.list.any { it.level == Level.WARN },
            "a migrations feature that runs at startup and finds nothing must WARN, not " +
                "log at INFO - otherwise a boot that manages no schema looks healthy. " +
                "Captured: ${appender.list.map { "${it.level}: ${it.formattedMessage}" }}"
        )
    }

    @Test
    fun `runAtStartup false does not warn`() {
        val feature = MigrationFeature(MigrationOptions(runAtStartup = false))

        feature.onReady(KatalystBeanContext(EmptyContainer()))

        assertTrue(
            appender.list.none { it.level == Level.WARN },
            "explicitly disabling startup migrations is a deliberate choice and must stay quiet"
        )
    }
}
