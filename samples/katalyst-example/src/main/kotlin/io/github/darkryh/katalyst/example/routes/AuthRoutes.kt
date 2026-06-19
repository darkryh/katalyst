package io.github.darkryh.katalyst.example.routes

import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.example.service.AuthenticationService
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(
    service: AuthenticationService,
) = katalystRouting {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = service.register(request)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = service.login(request)
            call.respond(response)
        }
    }
}
