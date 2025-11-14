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

        assertFailsWith<IllegalStateException> {
            builder.initializeDI()
        }
    }

    @Test
    fun `resolveServerConfiguration defaults to netty`() {
        val builder = KatalystApplicationBuilder()
        val config = builder.resolveServerConfiguration()

        assertEquals("netty", config.engineType.lowercase())
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
