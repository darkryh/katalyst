# Add WebSockets

Katalyst registers WebSocket routes automatically, the same way it registers HTTP routes.
You enable the feature, write an extension function with the `katalystWebSockets` DSL, and
place it under a scanned package.

## Enable WebSockets

```kotlin
features {
    enableWebSockets {
        // pingPeriod = 30.seconds
        // timeout = 15.seconds
        // maxFrameSize = Long.MAX_VALUE
        // masking = false
    }
}
```

The options block configures the underlying Ktor WebSockets plugin; all values are optional.
Add the `katalyst-websockets` dependency for the `enableWebSockets()` toggle. The routing
DSL and options live in `katalyst-ktor`.

## Define a WebSocket route

Write an extension function on `Route` that calls `katalystWebSockets`, then declare
`webSocket("/path") { … }` blocks inside it.

```kotlin
import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        for (frame in incoming) {
            if (frame is Frame.Text && frame.readText() == "ping") {
                send(Frame.Text("""{"type":"pong","timestamp":${System.currentTimeMillis()}}"""))
            }
        }
    }
}
```

Resolve dependencies inside the block with `ktInject<T>()`, just as in routes and middleware.

## Verify

Connect with a WebSocket client and exchange a message:

```bash
# using websocat
websocat ws://localhost:8080/ws/users
{"type":"welcome"}
ping
{"type":"pong","timestamp":1718700000000}
```

In tests, install the Ktor `WebSockets` client plugin and reuse the provided `client` inside
`katalystTestApplication` — see [Test your application](test-applications.md).

## Related

- [Ktor integration reference](../reference/ktor.md) — `katalystWebSockets` and
  `WebSocketOptions`.
- [Add routes, middleware, and exception handlers](add-routes-and-middleware.md) — the other
  Ktor entry points.

