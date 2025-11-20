package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.dto.UserProfileResponse
import com.ead.katalyst.example.config.security.AuthPrincipal
import com.ead.katalyst.example.service.UserProfileService
import com.ead.katalyst.ktor.builder.katalystRouting
import com.ead.katalyst.ktor.extension.ktInject
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Suppress("unused")
fun Route.userRoutes() = katalystRouting {
    authenticate("auth-jwt") {
        route("/api/users") {
            get {
                val service by call.ktInject<UserProfileService>()
                val users = service.listProfiles().map(UserProfileResponse::from)
                call.respond(users)
            }

            get("/{id}") {
                val service by call.ktInject<UserProfileService>()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
                val user = service.getProfile(id)
                call.respond(UserProfileResponse.from(user))
            }

            get("/me") {
                val principal = call.principal<AuthPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val service by call.ktInject<UserProfileService>()
                val profile = service.getProfileForAccount(principal.accountId)

                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(UserProfileResponse.from(profile))
            }
        }
    }
}
