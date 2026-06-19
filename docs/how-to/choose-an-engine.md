# Choose a server engine

Katalyst runs on any of three Ktor server engines: Netty, Jetty, or CIO. You select one in
the bootstrap by passing the engine object to `engine(...)`. Each engine ships in its own
module so you only depend on the one you use.

## Select an engine

Add the matching dependency and pass the engine object:

=== "Netty"

    ```kotlin
    // build.gradle.kts
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-netty:1.0.0-alpha")
    ```

    ```kotlin
    import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

    fun main(args: Array<String>) = katalystApplication(args) {
        engine(NettyServer)
        // …
    }
    ```

=== "Jetty"

    ```kotlin
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-jetty:1.0.0-alpha")
    ```

    ```kotlin
    import io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer

    fun main(args: Array<String>) = katalystApplication(args) {
        engine(JettyServer)
        // …
    }
    ```

=== "CIO"

    ```kotlin
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-cio:1.0.0-alpha")
    ```

    ```kotlin
    import io.github.darkryh.katalyst.ktor.engine.cio.CioServer

    fun main(args: Array<String>) = katalystApplication(args) {
        engine(CioServer)
        // …
    }
    ```

Each of `NettyServer`, `JettyServer`, and `CioServer` is an object implementing
`KatalystServerEngine`, so swapping engines is a one-line change plus the dependency.

## Which one

| Engine | Notes |
|--------|-------|
| **Netty** | The common default for production HTTP services. |
| **Jetty** | Servlet-based; useful in Jetty-aligned environments. Honors `maxThreads` / `minThreads` in the deployment config. |
| **CIO** | Coroutine-based pure-Kotlin engine with no extra native dependencies. |

## Configure the engine

Engine-level settings (host, port, thread pools, timeouts, TLS) come from the
`ktor.deployment` block when you enable server configuration:

```kotlin
features { enableServerConfiguration() }
```

The deployment keys, including which are engine-specific, are documented in the
[configuration reference](../reference/configuration.md#server-deployment-keys).

## Related

- [Application DSL reference](../reference/application-dsl.md) — the `engine(...)` block.
- [Configure with YAML](configure-yaml.md) — the server deployment block.

