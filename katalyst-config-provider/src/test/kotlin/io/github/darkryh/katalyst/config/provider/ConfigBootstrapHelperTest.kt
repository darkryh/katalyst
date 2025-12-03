package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.config.ConfigValidator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

class ConfigBootstrapHelperTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `loadDatabaseConfigMap extracts required keys with defaults`() {
        val config = mapConfigProvider(
            mapOf(
                "database.url" to "jdbc:h2:mem:test",
                "database.username" to "sa",
                "database.driver" to "org.h2.Driver"
            )
        )

        val result = ConfigBootstrapHelper.loadDatabaseConfigMap(config)

        assertEquals("jdbc:h2:mem:test", result["url"])
        assertEquals("org.h2.Driver", result["driver"])
        assertEquals(10, result["maxPoolSize"])
        assertEquals(2, result["minIdleConnections"])
        assertEquals(30_000L, result["connectionTimeout"])
        assertEquals("", result["password"])
    }

    @Test
    fun `loadServiceConfig wraps loader failures as ConfigException`() {
        val config = mapConfigProvider(emptyMap())
        val loader = object : ServiceConfigLoader<String> {
            override fun loadConfig(provider: ConfigProvider): String {
                throw IllegalStateException("boom")
            }
        }

        assertFailsWith<ConfigException> {
            ConfigBootstrapHelper.loadServiceConfig(config, loader)
        }
    }

    @Test
    fun `validateConfiguration aggregates validator failures`() {
        val config = mapConfigProvider(emptyMap())
        startKoin {
            modules(
                module {
                    single<ConfigValidator>(named("invalid")) {
                        object : ConfigValidator {
                            override fun validate() {
                                throw ConfigException("invalid settings")
                            }
                        }
                    }
                    single<ConfigValidator>(named("valid")) {
                        object : ConfigValidator {
                            override fun validate() {
                                // pass
                            }
                        }
                    }
                }
            )
        }

        assertFailsWith<ConfigException> {
            ConfigBootstrapHelper.validateConfiguration(config)
        }
    }

    private fun mapConfigProvider(entries: Map<String, Any>): ConfigProvider = object : ConfigProvider {
        private val backing = entries.toMutableMap()

        override fun <T> get(key: String, defaultValue: T?): T? {
            val value = backing[key] ?: return defaultValue
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        override fun getString(key: String, default: String): String {
            return backing[key]?.toString() ?: default
        }

        override fun getInt(key: String, default: Int): Int {
            return (backing[key] as? Number)?.toInt()
                ?: backing[key]?.toString()?.toIntOrNull()
                ?: default
        }

        override fun getLong(key: String, default: Long): Long {
            return (backing[key] as? Number)?.toLong()
                ?: backing[key]?.toString()?.toLongOrNull()
                ?: default
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            return (backing[key] as? Boolean) ?: default
        }

        override fun getList(key: String, default: List<String>): List<String> {
            val value = backing[key] ?: return default
            return when (value) {
                is List<*> -> value.map { it.toString() }
                is String -> value.split(",").map { it.trim() }
                else -> default
            }
        }

        override fun hasKey(key: String): Boolean = backing.containsKey(key)

        override fun getAllKeys(): Set<String> = backing.keys
    }
}
