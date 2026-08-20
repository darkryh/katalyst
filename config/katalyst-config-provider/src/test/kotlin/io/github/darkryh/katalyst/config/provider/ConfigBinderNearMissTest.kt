package io.github.darkryh.katalyst.config.provider

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A setting the operator wrote must never be ignored in silence.
 *
 * `ConfigBinder` falls back to a Kotlin default whenever a key is absent, and did so without a word
 * at any log level. For a key nobody set that is correct and unremarkable. For a key the operator
 * *did* set — spelled `notification.baseUrl` where the binder wants `notification.base-url` — it is
 * the scheduler bug wearing a different hat: the value is configured, the framework uses the
 * default, nothing fails, and nothing is logged.
 *
 * The signal is therefore reserved for a near miss: the configuration carries a key that normalises
 * to the one being looked for. Blanket-logging every default would be noise nobody reads, and noise
 * nobody reads is how the original defect stayed invisible.
 */
class ConfigBinderNearMissTest {

    private val binderLogger = LoggerFactory.getLogger(ConfigBinder::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        previousLevel = binderLogger.level
        binderLogger.level = Level.DEBUG
        appender.list.clear()
        appender.start()
        binderLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        binderLogger.detachAppender(appender)
        appender.stop()
        binderLogger.level = previousLevel
    }

    private fun warnings(): List<String> =
        appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    /** Minimal in-memory provider; only `hasKey`/`getAllKeys`/`get` matter for these paths. */
    private class MapProvider(private val values: Map<String, Any>) : ConfigProvider {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T?): T? = (values[key] as? T) ?: defaultValue
        override fun getString(key: String, default: String): String = values[key]?.toString() ?: default
        override fun getInt(key: String, default: Int): Int = values[key]?.toString()?.toIntOrNull() ?: default
        override fun getLong(key: String, default: Long): Long = values[key]?.toString()?.toLongOrNull() ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean =
            values[key]?.toString()?.toBooleanStrictOrNull() ?: default
        override fun getList(key: String, default: List<String>): List<String> = default
        override fun hasKey(key: String): Boolean = values.containsKey(key)
        override fun getAllKeys(): Set<String> = values.keys
    }

    @ConfigPrefix("notification")
    data class NotificationConfig(
        val baseUrl: String = "https://default.example.com",
        val retries: Int = 3,
    )

    @Test
    fun `a camelCase key the binder cannot match is reported, not silently ignored`() {
        // The operator wrote the setting. The binder wants kebab-case. Before this warning existed,
        // the application simply ran on the default and said nothing.
        val provider = MapProvider(mapOf("notification.baseUrl" to "https://real.example.com"))

        val bound = ConfigBinder.bind(NotificationConfig::class, provider) as NotificationConfig

        assertEquals(
            "https://default.example.com",
            bound.baseUrl,
            "sanity: the mismatched key genuinely does not bind",
        )
        val warning = warnings().singleOrNull { it.contains("notification.baseUrl") }
        assertTrue(
            warning != null,
            "a set-but-unmatched key must warn; warnings were ${warnings()}",
        )
        assertTrue(
            warning!!.contains("notification.base-url"),
            "the warning must name the key the binder actually wants: $warning",
        )
    }

    @Test
    fun `a snake_case key is reported the same way`() {
        val provider = MapProvider(mapOf("notification.base_url" to "https://real.example.com"))

        ConfigBinder.bind(NotificationConfig::class, provider)

        assertTrue(
            warnings().any { it.contains("notification.base_url") },
            "snake_case near miss must warn; got ${warnings()}",
        )
    }

    @Test
    fun `a key differing only in case is reported`() {
        val provider = MapProvider(mapOf("notification.BASE-URL" to "https://real.example.com"))

        ConfigBinder.bind(NotificationConfig::class, provider)

        assertTrue(warnings().any { it.contains("notification.BASE-URL") }, "got ${warnings()}")
    }

    @Test
    fun `an unset optional key produces no warning`() {
        // The common case, and the reason this is not a blanket log: an application that leaves
        // optional settings alone must not be told about it on every boot.
        val provider = MapProvider(emptyMap())

        val bound = ConfigBinder.bind(NotificationConfig::class, provider) as NotificationConfig

        assertEquals("https://default.example.com", bound.baseUrl)
        assertEquals(3, bound.retries)
        assertTrue(
            warnings().isEmpty(),
            "leaving an optional key unset is normal and must stay quiet; got ${warnings()}",
        )
    }

    @Test
    fun `an unrelated key present in the configuration does not trigger a warning`() {
        val provider = MapProvider(mapOf("notification.somethingElse" to "x", "server.port" to "8080"))

        ConfigBinder.bind(NotificationConfig::class, provider)

        assertTrue(
            warnings().isEmpty(),
            "only a near miss may warn, not any unrelated key; got ${warnings()}",
        )
    }

    @Test
    fun `a correctly spelled key binds and stays quiet`() {
        val provider = MapProvider(mapOf("notification.base-url" to "https://real.example.com"))

        val bound = ConfigBinder.bind(NotificationConfig::class, provider) as NotificationConfig

        assertEquals("https://real.example.com", bound.baseUrl)
        assertTrue(warnings().isEmpty(), "a matched key must not warn; got ${warnings()}")
    }

    @Test
    fun `every mismatched key is reported, not just the first`() {
        val provider = MapProvider(
            mapOf(
                "notification.baseUrl" to "https://real.example.com",
                "notification.Retries" to "9",
            )
        )

        ConfigBinder.bind(NotificationConfig::class, provider)

        assertTrue(warnings().any { it.contains("notification.baseUrl") }, "got ${warnings()}")
        assertTrue(warnings().any { it.contains("notification.Retries") }, "got ${warnings()}")
    }
}
