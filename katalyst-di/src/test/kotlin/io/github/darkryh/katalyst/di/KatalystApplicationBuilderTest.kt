package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.testing.core.testEmbeddedServer
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KatalystApplicationBuilderTest {

    @Test
    fun `initializeDI throws when database configuration is missing`() {
        val builder = KatalystApplicationBuilder()

        assertFailsWith<IllegalStateException> {
            builder.engine(testEmbeddedServer()).initializeDI()
        }
    }

    @Test
    fun `resolveServerConfiguration throws when engine not explicitly set`() {
        val builder = KatalystApplicationBuilder()

        assertFailsWith<IllegalStateException> {
            builder.resolveServerConfiguration()
        }
    }

    @Test
    fun `resolveServerConfiguration uses explicitly set engine`() {
        // Engine required; ensure exception is not thrown when set
        val builder = KatalystApplicationBuilder()
        builder
            .engine(testEmbeddedServer())
            .configuration(testConfigProvider())
        val config = builder.resolveServerConfiguration()
        assertEquals(true, config.engine != null)
    }

    @Test
    fun `resolveServerConfiguration fails when configuration source is missing`() {
        val builder = KatalystApplicationBuilder()
            .engine(testEmbeddedServer())

        val exception = assertFailsWith<IllegalStateException> {
            builder.resolveServerConfiguration()
        }

        assertTrue(exception.message.orEmpty().contains("No configuration source configured"))
    }

    @Test
    fun `configuration installed inside features block feeds server resolution`() {
        // Change F: enableYamlConfiguration() now lives inside features { }; it routes through
        // KatalystFeaturesBuilder.configuration(), which installs the source on the builder
        // synchronously (before resolution) so Phase-0 ordering is preserved.
        val builder = KatalystApplicationBuilder()
            .engine(testEmbeddedServer())
        builder.features { configuration(testConfigProvider("ktor.deployment.port" to 9999)) }

        val config = builder.resolveServerConfiguration()

        assertEquals(9999, config.deployment.port)
    }

    @Test
    fun `engine selection is mandatory before initializeDI`() {
        val builder = KatalystApplicationBuilder()

        builder.database(
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )

        // Should throw because engine() was not called
        val exception = assertFailsWith<IllegalStateException> {
            builder.initializeDI()
        }

        assertEquals(true, exception.message?.contains("Engine must be explicitly selected"))
    }

    @Test
    fun `bean engine selection is mandatory before initializeDI`() {
        val builder = KatalystApplicationBuilder()
            .engine(testEmbeddedServer())
            .configuration(testConfigProvider())
            .database(
                DatabaseConfig(
                    url = "jdbc:h2:mem:test",
                    driver = "org.h2.Driver",
                    username = "sa",
                    password = ""
                )
            )

        val exception = assertFailsWith<IllegalStateException> {
            builder.initializeDI()
        }

        assertTrue(exception.message.orEmpty().contains("Bean engine must be explicitly selected"))
    }

    @Test
    fun `feature registration preserves insertion order`() {
        val builder = KatalystApplicationBuilder()
        val featureA = object : KatalystFeature { override val id: String = "feature-a" }
        val featureB = object : KatalystFeature { override val id: String = "feature-b" }

        builder
            .database(
                DatabaseConfig(
                    url = "jdbc:h2:mem:test",
                    driver = "org.h2.Driver",
                    username = "sa",
                    password = ""
                )
            )
            .feature(featureA)
            .feature(featureB)

        val registered = builder.registeredFeatures()

        val ids = registered.map { it.id }
        val idxA = ids.indexOf("feature-a")
        val idxB = ids.indexOf("feature-b")

        assertTrue(idxA >= 0, "feature-a should be registered")
        assertTrue(idxB > idxA, "feature-b should be registered after feature-a")
    }

    @Test
    fun `feature scope registers optional features in insertion order`() {
        val builder = KatalystApplicationBuilder()
        val featureA = object : KatalystFeature { override val id: String = "feature-a" }
        val featureB = object : KatalystFeature { override val id: String = "feature-b" }

        builder.features {
            feature(featureA)
            feature(featureB)
        }

        val ids = builder.registeredFeatures().map { it.id }
        val idxA = ids.indexOf("feature-a")
        val idxB = ids.indexOf("feature-b")

        assertTrue(idxA >= 0, "feature-a should be registered through the feature scope")
        assertTrue(idxB > idxA, "feature-b should be registered after feature-a")
    }

    @Test
    fun `database DSL loads database config from configured provider`() {
        val builder = KatalystApplicationBuilder()
            .configuration(
                testConfigProvider(
                    "database.url" to "jdbc:h2:mem:configured",
                    "database.driver" to "org.h2.Driver",
                    "database.username" to "configured-user",
                    "database.password" to "configured-password",
                )
            )
            .database {
                fromConfiguration()
            }

        val config = resolveDatabaseConfig(builder)

        assertEquals("jdbc:h2:mem:configured", config.url)
        assertEquals("org.h2.Driver", config.driver)
        assertEquals("configured-user", config.username)
        assertEquals("configured-password", config.password)
        assertEquals(10, config.maxPoolSize)
        assertEquals(2, config.minIdleConnections)
        assertEquals(30_000L, config.connectionTimeout)
        assertEquals(600_000L, config.idleTimeout)
        assertEquals(1_800_000L, config.maxLifetime)
        assertEquals(false, config.autoCommit)
    }

    @Test
    fun `database DSL allows code overrides after loading configuration`() {
        val builder = KatalystApplicationBuilder()
            .configuration(
                testConfigProvider(
                    "database.url" to "jdbc:h2:mem:configured",
                    "database.driver" to "org.h2.Driver",
                    "database.username" to "configured-user",
                    "database.password" to "configured-password",
                    "database.pool.maxSize" to 12,
                )
            )
            .database {
                fromConfiguration()
                maxPoolSize = 20
                minIdleConnections = 4
                autoCommit = true
            }

        val config = resolveDatabaseConfig(builder)

        assertEquals("jdbc:h2:mem:configured", config.url)
        assertEquals(20, config.maxPoolSize)
        assertEquals(4, config.minIdleConnections)
        assertEquals(true, config.autoCommit)
    }

    private fun resolveDatabaseConfig(builder: KatalystApplicationBuilder): DatabaseConfig {
        val method = KatalystApplicationBuilder::class.java
            .getDeclaredMethod("resolveDatabaseConfigOrThrow")
            .apply { isAccessible = true }
        return method.invoke(builder) as DatabaseConfig
    }

    private fun testConfigProvider(
        vararg overrides: Pair<String, Any?>,
    ): ConfigProvider = object : ConfigProvider {
        private val values = mapOf(
            "ktor.deployment.host" to "0.0.0.0",
            "ktor.deployment.port" to 8080,
            "ktor.deployment.shutdownGracePeriod" to 1000L,
            "ktor.deployment.shutdownTimeout" to 5000L,
            "ktor.deployment.connectionGroupSize" to 8,
            "ktor.deployment.workerGroupSize" to 8,
            "ktor.deployment.callGroupSize" to 8,
            "ktor.deployment.maxInitialLineLength" to 4096,
            "ktor.deployment.maxHeaderSize" to 8192,
            "ktor.deployment.maxChunkSize" to 8192,
            "ktor.deployment.connectionIdleTimeoutMs" to 180000L,
            *overrides,
        )

        override fun <T> get(key: String, defaultValue: T?): T? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? T ?: defaultValue
        }

        override fun getString(key: String, default: String): String = values[key]?.toString() ?: default

        override fun getInt(key: String, default: Int): Int =
            (values[key] as? Number)?.toInt() ?: default

        override fun getLong(key: String, default: Long): Long =
            (values[key] as? Number)?.toLong() ?: default

        override fun getBoolean(key: String, default: Boolean): Boolean =
            (values[key] as? Boolean) ?: default

        override fun getList(key: String, default: List<String>): List<String> = default

        override fun hasKey(key: String): Boolean = values.containsKey(key)

        override fun getAllKeys(): Set<String> = values.keys
    }
}
