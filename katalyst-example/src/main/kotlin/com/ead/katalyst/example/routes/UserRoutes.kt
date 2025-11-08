package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.UserProfileResponse
import com.ead.katalyst.example.security.AuthPrincipal
import com.ead.katalyst.example.service.UserProfileService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystRouting
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

@Suppress("unused")
fun Route.userRoutes() = katalystRouting {
    authenticate("auth-jwt") {
        route("/api/users") {
            get {
                val service = call.inject<UserProfileService>()
                val users = service.listProfiles().map(UserProfileResponse::from)
                call.respond(users)
            }

            get("/{id}") {
                val service = call.inject<UserProfileService>()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
                val user = service.getProfile(id)
                call.respond(UserProfileResponse.from(user))
            }

            get("/me") {
                val principal = call.principal<AuthPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val service = call.inject<UserProfileService>()
                val profile = service.getProfileForAccount(principal.accountId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(UserProfileResponse.from(profile))
            }
        }
    }
}
