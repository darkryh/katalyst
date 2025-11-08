package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.LoginRequest
import com.ead.katalyst.example.api.RegisterRequest
import com.ead.katalyst.example.service.AuthenticationService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystRouting
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
            val service = call.inject<AuthenticationService>()
            val request = call.receive<RegisterRequest>()
            val response = service.register(request)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val service = call.inject<AuthenticationService>()
            val request = call.receive<LoginRequest>()
            val response = service.login(request)
            call.respond(response)
        }
    }
}
