package io.github.darkryh.katalyst.config.spi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [ConfigLoaderResolver]'s format-selection and ServiceLoader routing, using
 * [FakeYamlConfigLoader] registered via `META-INF/services`.
 */
class ConfigLoaderResolverTest {

    @Test
    fun `routes a yaml path to the yaml loader`() {
        val config = ConfigLoaderResolver.load(listOf("application.yaml"))
        assertEquals("fake-yaml", config.property("loaded.by").getString())
        assertEquals(listOf("application.yaml"), FakeYamlConfigLoader.lastLoadedPaths)
    }

    @Test
    fun `yml extension also resolves to the yaml loader`() {
        ConfigLoaderResolver.load(listOf("application.yml"))
        assertEquals(listOf("application.yml"), FakeYamlConfigLoader.lastLoadedPaths)
    }

    @Test
    fun `an explicit format hint overrides the path extension`() {
        // Path looks like hocon, but the YAML hint must win and route to the yaml loader.
        ConfigLoaderResolver.load(listOf("weird.conf"), formatHint = ConfigFormat.YAML)
        assertEquals(listOf("weird.conf"), FakeYamlConfigLoader.lastLoadedPaths)
    }

    @Test
    fun `an unsupported format fails with a clear, actionable error`() {
        val error = assertFailsWith<IllegalStateException> {
            ConfigLoaderResolver.load(listOf("application.conf")) // hocon: no loader registered
        }
        assertTrue(error.message?.contains("No ConfigLoader found") == true, "got: ${error.message}")
        assertTrue(error.message?.contains("hocon") == true)
    }

    @Test
    fun `profiled path resolution delegates to a profile-aware loader`() {
        val paths = ConfigLoaderResolver.resolveProfiledPaths(baseName = "application")
        assertEquals(listOf("application.yaml", "application-test.yaml"), paths)
    }
}
