package com.ead.katalyst.config.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlConfigProviderIntegrationTest {

    @Test
    fun `provider applies profile loader and environment substitution`() {
        val provider = YamlConfigProvider(
            profileLoader = YamlProfileLoader(
                profileEnvVar = "KTL_PROFILE_TEST",
                baseConfigFile = "application.yaml",
                environmentReader = { "test" }
            )
        )

        assertEquals("jdbc:test://localhost", provider.getString("database.url"))
        assertEquals(5, provider.getInt("database.retries"))
        assertTrue(provider.getBoolean("feature.enabled"))
    }

    @Test
    fun `provider fails fast when required keys are missing`() {
        kotlin.test.assertFailsWith<com.ead.katalyst.core.config.ConfigException> {
            YamlConfigProvider(
                profileLoader = YamlProfileLoader(
                    baseConfigFile = "application-missing-required.yaml",
                    environmentReader = { null }
                )
            )
        }
    }
}
