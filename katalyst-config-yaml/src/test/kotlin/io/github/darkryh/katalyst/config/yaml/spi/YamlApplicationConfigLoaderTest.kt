package io.github.darkryh.katalyst.config.yaml.spi

import io.github.darkryh.katalyst.config.spi.ConfigLoaderResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class YamlApplicationConfigLoaderTest {

    @Test
    fun `list values survive profile merge and flatten round-trip`() {
        val loader = YamlApplicationConfigLoader()

        val config = loader.load(
            listOf("application-lists-base.yaml", "application-lists-dev.yaml")
        )

        // Overridden by the profile file: full replacement list, all elements, correct order.
        val hosts = config.property("server.hosts").getList()
        assertEquals(listOf("host-x", "host-y", "host-z"), hosts)

        // Untouched by the profile file: base list must still be intact (not dropped/collapsed).
        val origins = config.property("cors.allowedOrigins").getList()
        assertEquals(listOf("https://base.example.com"), origins)
    }

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
