package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.dto.AuthResponse
import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.api.dto.UserProfileResponse
import com.ead.katalyst.example.service.UserProfileService
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import com.ead.katalyst.testing.ktor.katalystTestApplication
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExampleApiE2ETest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `register login and fetch profile over HTTP`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("com.ead.katalyst.example")
        }
    ) { environment ->
        val email = "integration-${System.currentTimeMillis()}@example.com"
        val registerRequest = RegisterRequest(
            email = email,
            password = "Sup3rSecure!",
            displayName = "Integration User"
        )

        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(registerRequest))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val registerBody = json.decodeFromString<AuthResponse>(registerResponse.bodyAsText())
        assertTrue(registerBody.token.isNotBlank())

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(email = email, password = registerRequest.password)))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginBody = json.decodeFromString<AuthResponse>(loginResponse.bodyAsText())
        assertEquals(registerBody.email, loginBody.email)

        val profileResponse = client.get("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${registerBody.token}")
        }
        assertEquals(HttpStatusCode.OK, profileResponse.status)
        val profileBody = json.decodeFromString<UserProfileResponse>(profileResponse.bodyAsText())
        assertEquals(registerBody.accountId, profileBody.accountId)
        assertEquals(registerRequest.displayName, profileBody.displayName)

        val service = environment.get<UserProfileService>()
        assertTrue(service.listProfiles().any { it.accountId == registerBody.accountId })
    }
}
