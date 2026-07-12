package io.github.darkryh.katalyst.di.config

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for finding D: [ApplicationConfigProvider.getAllKeys] used to always
 * return an empty set, contradicting [ApplicationConfigProvider.hasKey]/`get`.
 */
class ConfigProvidersTest {

    @Test
    fun `ApplicationConfigProvider getAllKeys returns the actual configured keys`() {
        val config = MapApplicationConfig(
            "ktor.deployment.host" to "0.0.0.0",
            "ktor.deployment.port" to "8080"
        )
        val provider = ApplicationConfigProvider(config)

        val keys = provider.getAllKeys()

        assertEquals(setOf("ktor.deployment.host", "ktor.deployment.port"), keys)
    }

    @Test
    fun `ApplicationConfigProvider getAllKeys is empty when no keys are configured`() {
        val provider = ApplicationConfigProvider(MapApplicationConfig())

        assertEquals(emptySet(), provider.getAllKeys())
    }

    @Test
    fun `CompositeConfigProvider getAllKeys unions keys from all providers`() {
        val first = ApplicationConfigProvider(MapApplicationConfig("a.key" to "1"))
        val second = ApplicationConfigProvider(MapApplicationConfig("b.key" to "2"))
        val composite = CompositeConfigProvider(listOf(first, second))

        assertEquals(setOf("a.key", "b.key"), composite.getAllKeys())
    }
}
