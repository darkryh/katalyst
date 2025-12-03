package io.github.darkryh.katalyst.example.routes

import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.example.service.AuthenticationService
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

@Suppress("unused")
fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/register") {
            val service by call.ktInject<AuthenticationService>()
            val request = call.receive<RegisterRequest>()
            val response = service.register(request)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val service by call.ktInject<AuthenticationService>()
            val request = call.receive<LoginRequest>()
            val response = service.login(request)
            call.respond(response)
        }
    }
}
