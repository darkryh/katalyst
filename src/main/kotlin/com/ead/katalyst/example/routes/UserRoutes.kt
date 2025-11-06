package com.ead.katalyst.example.routes

import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.api.UserResponse
import com.ead.katalyst.example.service.UserService
import com.ead.katalyst.routes.inject
import com.ead.katalyst.routes.katalystRouting
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

@Suppress("unused")
fun Route.userRoutes() = katalystRouting {
    route("/api/users") {
        post {
            val service = call.inject<UserService>()
            val request = call.receive<CreateUserRequest>()
            val created = service.createUser(request)
            call.respond(HttpStatusCode.Created, UserResponse.from(created))
        }

        get {
            val service = call.inject<UserService>()
            val users = service.listUsers().map(UserResponse::from)
            call.respond(users)
        }

        get("/{id}") {
            val service = call.inject<UserService>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
            val user = service.getUser(id)
            call.respond(UserResponse.from(user))
        }
    }
}
