package com.ead.katalyst.config.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlProfileLoaderTest {

    @Test
    fun `loadConfiguration returns base config when profile unset`() {
        val loader = YamlProfileLoader(
            profileEnvVar = "KTL_PROFILE_TEST",
            baseConfigFile = "application.yaml",
            environmentReader = { null }
        )

        val config = loader.loadConfiguration()

        @Suppress("UNCHECKED_CAST")
        val database = config["database"] as Map<String, Any>
        assertEquals("jdbc:h2:mem:base", database["url"])
        assertEquals(1, database["retries"])
        assertTrue(!config.containsKey("profileOnly"))
    }

    @Test
    fun `loadConfiguration merges profile configuration when present`() {
        val loader = YamlProfileLoader(
            profileEnvVar = "KTL_PROFILE_TEST",
            baseConfigFile = "application.yaml",
            environmentReader = { "test" }
        )

        val config = loader.loadConfiguration()
        @Suppress("UNCHECKED_CAST")
        val database = config["database"] as Map<String, Any>
        assertEquals(5, database["retries"])

        @Suppress("UNCHECKED_CAST")
        val feature = config["feature"] as Map<String, Any>
        assertEquals(true, feature["enabled"])
        assertEquals(true, config["profileOnly"])
    }

    @Test
    fun `loadConfiguration fails when requested profile file is missing`() {
        val loader = YamlProfileLoader(
            profileEnvVar = "KTL_PROFILE_TEST",
            baseConfigFile = "application.yaml",
            environmentReader = { "missing-profile" }
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            loader.loadConfiguration()
        }
    }

    @Test
    fun `profile overrides base values and falls back when missing`() {
        val loader = YamlProfileLoader(
            profileEnvVar = "KTL_PROFILE_TEST",
            baseConfigFile = "application.yaml",
            environmentReader = { "partial" }
        )

        val config = loader.loadConfiguration()
        @Suppress("UNCHECKED_CAST")
        val database = config["database"] as Map<String, Any>
        // port overridden by profile
        assertEquals(9090, database["port"])
        // retries comes from base (missing in profile)
        assertEquals(1, database["retries"])
    }
}
