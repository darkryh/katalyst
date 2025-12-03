package io.github.darkryh.katalyst.config.yaml.spi

import io.github.darkryh.katalyst.config.spi.ConfigLoaderResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class YamlApplicationConfigLoaderTest {

    @Test
    fun `profile property adds profile-specific path`() {
        val previous = System.getProperty("katalyst.profile")

        try {
            System.setProperty("katalyst.profile", "dev")

            val paths = ConfigLoaderResolver.resolveProfiledPaths(baseName = "application")

            assertEquals(listOf("application.yaml", "application-dev.yaml"), paths)
        } finally {
            if (previous == null) {
                System.clearProperty("katalyst.profile")
            } else {
                System.setProperty("katalyst.profile", previous)
            }
        }
    }
}
