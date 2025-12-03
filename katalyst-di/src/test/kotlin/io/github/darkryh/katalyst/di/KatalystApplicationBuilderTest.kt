package io.github.darkryh.katalyst.di

import io.github.darkryh.katalyst.config.DatabaseConfig
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
        builder.engine(testEmbeddedServer())
        val config = builder.resolveServerConfiguration()
        assertEquals(true, config.engine != null)
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
}
