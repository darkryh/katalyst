package com.ead.katalyst.di

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.di.feature.KatalystFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KatalystApplicationBuilderTest {

    @Test
    fun `initializeDI throws when database configuration is missing`() {
        val builder = KatalystApplicationBuilder()
        val mockEngine = com.ead.katalyst.di.config.test.MockEngine("netty")

        assertFailsWith<IllegalStateException> {
            builder.engine(mockEngine).initializeDI()
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
        val mockEngine = com.ead.katalyst.di.config.test.MockEngine("netty")
        val builder = KatalystApplicationBuilder()
        builder.engine(mockEngine)
        val config = builder.resolveServerConfiguration()

        assertEquals("netty", config.engine.engineType.lowercase())
    }

    @Test
    fun `engine selection is mandatory before initializeDI`() {
        val builder = KatalystApplicationBuilder()
        val mockEngine = com.ead.katalyst.di.config.test.MockEngine("test")

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

        assertEquals(listOf("feature-a", "feature-b"), registered.map { it.id })
    }
}
