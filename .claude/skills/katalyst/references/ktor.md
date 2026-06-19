# Ktor integration reference

`katalyst-ktor` provides the DSLs Katalyst installs automatically once DI is ready. Each is an
extension function you place under a scanned package; inside any of them `ktInject` resolves
dependencies. WebSockets additionally need `features { enableWebSockets() }`.

## katalystRouting

```kotlin
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
fun Route.katalystRouting(block: Route.() -> Unit)
fun Application.katalystRouting(block: Routing.() -> Unit)
```

```kotlin
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

@Suppress("unused")
fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/login") {
            val service = call.ktInject<AuthenticationService>()
            call.respond(service.login(call.receive()))
        }
    }
}
```

Parameter-injected variant:

```kotlin
fun Route.authRoutes(service: AuthenticationService) = katalystRouting {
    post("/login") { call.respond(service.login(call.receive())) }
}
```

## katalystMiddleware

```kotlin
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
fun Application.katalystMiddleware(block: MiddlewareBuilder.() -> Unit)
```

Use it to install Ktor plugins or apply setup:

```kotlin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Suppress("unused")
fun Application.contentNegotiation() = katalystMiddleware {
    install(ContentNegotiation) { json() }
}

@Suppress("unused")
fun Application.security() = katalystMiddleware {
    val jwtSettings by ktInject<JwtSettingsService>()
    jwtSettings.configure(this@security)
}
```

Programmatic API also available: `Middleware` interface, `MiddlewareResult`.

## katalystWebSockets

```kotlin
import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
fun Route.katalystWebSockets(block: Route.() -> Unit)
```

```kotlin
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

@Suppress("unused")
fun Route.notificationRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        for (frame in incoming) {
            if (frame is Frame.Text && frame.readText() == "ping") {
                send(Frame.Text("""{"type":"pong"}"""))
            }
        }
    }
}
```

### WebSocketOptions (enableWebSockets { } receiver)

| Field | Meaning |
|-------|---------|
| `pingPeriod` | Interval between pings. |
| `timeout` | Inactivity timeout. |
| `maxFrameSize` | Max frame size (bytes). |
| `masking` | Mask outgoing frames. |

Note: older imports `io.github.darkryh.katalyst.websockets.*` still compile via a deprecated
path; new code imports from `io.github.darkryh.katalyst.ktor.websocket`. The `enableWebSockets()`
toggle remains in `katalyst-websockets`.

## katalystExceptionHandler

```kotlin
import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
fun Application.katalystExceptionHandler(block: ExceptionHandlerBuilder.() -> Unit)
```

```kotlin
@Suppress("unused")
fun Application.exceptionHandlers() = katalystExceptionHandler {
    exception<ValidationException> { call, exception ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message))
    }
    exception<Exception> { call, exception ->     // catch-all LAST
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Unexpected error"))
    }
}
```

`exception<T> { call, exception -> }` matches the thrown type. Register concrete types first,
catch-all `exception<Exception>` last. No match → default Ktor behavior.

## ktInject

```kotlin
import io.github.darkryh.katalyst.ktor.extension.ktInject
val service = call.ktInject<AuthService>()       // direct, on ApplicationCall
val settings by ktInject<JwtSettingsService>()    // delegate
```

Resolves from the same container that injects services — same singletons.

## Container access (advanced)

`katalystContainer()` / `getKatalystContainer()` (on `Application`) return the `KatalystContainer`;
`verifyKatalystContainer()` asserts it is initialized. Framework-level use; prefer `ktInject`.

## Checklist for a new HTTP feature

1. DTOs are `@Serializable` and JSON content negotiation is installed (a `katalystMiddleware`).
2. Route function is `fun Route.xxx() = katalystRouting { }`, under a scanned package,
   `@Suppress("unused")`.
3. Resolve services with `call.ktInject<T>()`.
4. Wrap service writes in `transactionManager.transaction { }`.
5. Add a `katalystExceptionHandler` for your domain exceptions if you want typed responses.
