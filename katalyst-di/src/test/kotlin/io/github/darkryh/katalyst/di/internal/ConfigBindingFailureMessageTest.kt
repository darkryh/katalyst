package io.github.darkryh.katalyst.di.internal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `ConfigBinder` lives in another module, so the orchestrator calls it through
 * `Method.invoke`. Anything the binder throws therefore arrives wrapped in an
 * [java.lang.reflect.InvocationTargetException], whose own message is `null`.
 *
 * The wrapper missed the `catch (e: KatalystDIException)` branch and fell through to the generic
 * handler, which logs `e.message`: a config class rejecting a value made boot die printing
 * "Error during configuration binding: null" while the real reason never appeared anywhere. This is
 * the same reflective-catch shape as the already-fixed `propertyAlreadyInitialised`.
 */
class ConfigBindingFailureMessageTest {

    private lateinit var engine: TestBeanEngine
    private lateinit var orchestratorLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeTest
    fun setUp() {
        RegistryManager.resetAll()
        engine = TestBeanEngine()
        // The engine must be started before beans go into it, exactly as production bootstraps
        // the container before wiring: KatalystBeanEngineContract pins that for every engine.
        engine.start(emptyList(), allowOverrides = true)
        engine.registerInstance(RejectingConfigProvider(port = 70000), ConfigProvider::class)

        orchestratorLogger = LoggerFactory.getLogger("ComponentRegistrationOrchestrator") as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        orchestratorLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        orchestratorLogger.detachAppender(appender)
        appender.stop()
        RegistryManager.resetAll()
        engine.stop()
    }

    @Test
    fun `a configuration class rejecting a value fails with its own exception type`() {
        val orchestrator = ComponentRegistrationOrchestrator(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("katalyst.ditest.config"),
        )

        val thrown = assertFailsWith<ConfigException> {
            orchestrator.registerAllWithValidation()
        }

        assertTrue(
            thrown.message!!.contains("port must be between 1 and 65535 but was 70000"),
            "the config class's own validation message must survive the reflective call: ${thrown.message}",
        )
    }

    @Test
    fun `the real reason is logged instead of a null message`() {
        val orchestrator = ComponentRegistrationOrchestrator(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("katalyst.ditest.config"),
        )

        runCatching { orchestrator.registerAllWithValidation() }

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertTrue(
            errors.any { it.formattedMessage.contains("port must be between 1 and 65535 but was 70000") },
            "boot must print the reason the configuration was rejected, got: " +
                "${errors.map { it.formattedMessage }}",
        )
        assertTrue(
            errors.none { it.formattedMessage.contains("Error during configuration binding: null") },
            "the InvocationTargetException's null message must never reach the log: " +
                "${errors.map { it.formattedMessage }}",
        )
    }

    /** Serves exactly the one key `RejectingConfig` binds, with a value it refuses. */
    private class RejectingConfigProvider(private val port: Int) : ConfigProvider {
        private val values: Map<String, Any> = mapOf("rejecting.port" to port)

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T?): T? = (values[key] as? T) ?: defaultValue
        override fun getString(key: String, default: String): String = values[key]?.toString() ?: default
        override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
        override fun getLong(key: String, default: Long): Long = (values[key] as? Int)?.toLong() ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
        override fun getList(key: String, default: List<String>): List<String> = default
        override fun hasKey(key: String): Boolean = key in values
        override fun getAllKeys(): Set<String> = values.keys
    }
}
