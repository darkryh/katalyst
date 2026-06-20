# Add routes, middleware, and exception handlers

Katalyst installs three kinds of Ktor entry point automatically once dependency injection is
ready: routing, middleware, and exception handlers. You write an extension function with the
matching DSL, place it under a scanned package, and Katalyst registers it. This guide shows
each one.

## Add routes

Write an extension function on `Route` (or `Application`) that calls `katalystRouting`.
Resolve dependencies inside handlers with `call.ktInject<T>()`.

```kotlin
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/register") {
            val service = call.ktInject<AuthenticationService>()
            call.respond(HttpStatusCode.Created, service.register(call.receive()))
        }
        post("/login") {
            val service = call.ktInject<AuthenticationService>()
            call.respond(service.login(call.receive()))
        }
    }
}
```

You can also inject a dependency as a function parameter — Katalyst supplies it when it
invokes the function:

```kotlin
fun Route.authRoutes(service: AuthenticationService) = katalystRouting {
    post("/login") { call.respond(service.login(call.receive())) }
}
```

These functions are never called from your code — Katalyst discovers and invokes them for you.
Because of that, the IDE can flag them as unused. Install the
[Katalyst IDE plugin](install-ide-plugin.md), which teaches IntelliJ IDEA and Android Studio to
recognize Katalyst entrypoints, and the warning goes away — no `@Suppress` needed.

## Add middleware

Write an extension function on `Application` that calls `katalystMiddleware`. Use it to
install Ktor plugins or apply cross-cutting setup. Resolve dependencies with `ktInject`,
either as a delegate (`by`) or directly.

```kotlin
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun Application.contentNegotiation() = katalystMiddleware {
    install(ContentNegotiation) { json() }
}

fun Application.security() = katalystMiddleware {
    val jwtSettings by ktInject<JwtSettingsService>()
    jwtSettings.configure(this@security)
}
```

## Add exception handlers

Write an extension function on `Application` that calls `katalystExceptionHandler`, and
register typed handlers with `exception<T> { call, exception -> … }`. Katalyst matches the
thrown type and falls back to default Ktor behavior when no handler matches.

```kotlin
import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond

fun Application.exceptionHandlers() = katalystExceptionHandler {
    exception<ValidationException> { call, exception ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message))
    }
    exception<Exception> { call, exception ->
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Unexpected error"))
    }
}
```

Order from specific to general: register concrete exception types first and a catch-all
`exception<Exception>` last.

## Related

- [Ktor integration reference](../reference/ktor.md) — every DSL function and `ktInject`.
- [Add WebSockets](add-websockets.md) — the fourth Ktor entry point.
- [DI & auto-wiring](../reference/di-auto-wiring.md) — how discovery and injection work.

