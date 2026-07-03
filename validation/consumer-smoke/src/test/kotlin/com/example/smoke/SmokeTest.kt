package com.example.smoke

import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boots the whole Katalyst stack — DI, persistence (Exposed + H2), serialization and routing — from
 * artifacts resolved out of mavenLocal, with no Ktor/Exposed coordinates declared by this build.
 * Proves the starters carry everything a real app needs.
 */
class SmokeTest {
    @Test
    fun `boots and serves a route from published artifacts`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("com.example.smoke")
        },
    ) { _ ->
        val response = client.get("/smoke")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("smoke-widget"), "unexpected body: $body")
    }
}
