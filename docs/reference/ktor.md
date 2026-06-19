# Ktor integration

The `katalyst-ktor` module provides the DSLs Katalyst installs automatically once dependency
injection is ready: routing, middleware, WebSockets, and exception handlers. Each is an
extension function you place under a scanned package. Inside any of them, `ktInject` resolves
dependencies. For task help, see [Add routes, middleware, and exception handlers](../how-to/add-routes-and-middleware.md)
and [Add WebSockets](../how-to/add-websockets.md).

## katalystRouting

Registers routes. Available on both `Route` and `Application`.

```kotlin
fun Route.katalystRouting(block: Route.() -> Unit)
fun Application.katalystRouting(block: Routing.() -> Unit)
```

```kotlin
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

A route function may also declare injected parameters, supplied when Katalyst invokes it:

```kotlin
fun Route.authRoutes(service: AuthenticationService) = katalystRouting {
    post("/login") { call.respond(service.login(call.receive())) }
}
```

## katalystMiddleware

Installs Ktor plugins and applies cross-cutting setup. Defined on `Application`.

```kotlin
fun Application.katalystMiddleware(block: MiddlewareBuilder.() -> Unit)
```

```kotlin
@Suppress("unused")
fun Application.security() = katalystMiddleware {
    val jwtSettings by ktInject<JwtSettingsService>()
    jwtSettings.configure(this@security)
}
```

For programmatic middleware, the module also exposes the `Middleware` interface and
`MiddlewareResult`.

## katalystWebSockets

Registers WebSocket routes. Defined on `Route`. Requires `features { enableWebSockets() }`.

```kotlin
fun Route.katalystWebSockets(block: Route.() -> Unit)
```

```kotlin
@Suppress("unused")
fun Route.notificationRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        for (frame in incoming) { /* … */ }
    }
}
```

### WebSocket options

Configure the plugin in the bootstrap with `enableWebSockets { … }`, which builds a
`WebSocketOptions`:

| Field | Meaning |
|-------|---------|
| `pingPeriod` | Interval between ping frames. |
| `timeout` | Connection inactivity timeout. |
| `maxFrameSize` | Maximum frame size in bytes. |
| `masking` | Whether outgoing frames are masked. |

## katalystExceptionHandler

Registers typed exception handlers. Defined on `Application`.

```kotlin
fun Application.katalystExceptionHandler(block: ExceptionHandlerBuilder.() -> Unit)
```

```kotlin
@Suppress("unused")
fun Application.exceptionHandlers() = katalystExceptionHandler {
    exception<ValidationException> { call, exception ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message))
    }
    exception<Exception> { call, exception ->
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Unexpected error"))
    }
}
```

`exception<T> { call, exception -> … }` matches the thrown type. Register concrete types
first and a catch-all `exception<Exception>` last; if no handler matches, Katalyst falls back
to default Ktor behavior.

## ktInject

Resolves a dependency inside any Katalyst Ktor block. Two forms:

```kotlin
val service = call.ktInject<AuthService>()      // direct, on an ApplicationCall
val settings by ktInject<JwtSettingsService>()  // property delegate
```

`ktInject` resolves from the same container that injects your services, so request handlers
get the exact same singletons.

## Container access

`katalystContainer()` (on `Application`) and `getKatalystContainer()` return the active
`KatalystContainer`; `verifyKatalystContainer()` asserts it is initialized. These are mainly
for advanced or framework-level code — application code uses `ktInject`.

## Module migration note

Older code importing WebSocket APIs from `io.github.darkryh.katalyst.websockets.*` still
compiles through a deprecated compatibility path, but new code should import from
`io.github.darkryh.katalyst.ktor.websocket`. The `enableWebSockets()` toggle remains in
`katalyst-websockets`.

## See also

- [Add routes, middleware, and exception handlers](../how-to/add-routes-and-middleware.md)
- [Add WebSockets](../how-to/add-websockets.md)
- [DI & auto-wiring](di-auto-wiring.md) — how these functions are discovered.

