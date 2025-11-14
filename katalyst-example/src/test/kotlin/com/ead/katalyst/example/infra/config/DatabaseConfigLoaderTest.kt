package com.ead.katalyst.example.infra.config

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.testing.core.config.FakeConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseConfigLoaderTest {

    @Test
    fun `loadConfig pulls required fields and defaults`() {
        val provider = FakeConfigProvider(
            mapOf(
                "database.url" to "jdbc:h2:mem:test",
                "database.username" to "sa",
                "database.driver" to "org.h2.Driver"
            )
        )

        val config = DatabaseConfigLoader.loadConfig(provider)

        assertEquals("jdbc:h2:mem:test", config.url)
        assertEquals("org.h2.Driver", config.driver)
        assertEquals("sa", config.username)
        assertEquals("", config.password)
        assertEquals(10, config.maxPoolSize)
        assertEquals(2, config.minIdleConnections)
        assertFalse(config.autoCommit)
    }

    @Test
    fun `validate fails when driver class missing`() {
        val config = DatabaseConfig(
            url = "jdbc:test",
            driver = "com.example.DoesNotExist",
            username = "sa",
            password = ""
        )

        assertFailsWith<ConfigException> {
            DatabaseConfigLoader.validate(config)
        }
    }
}
