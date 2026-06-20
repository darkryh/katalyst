# Choose a server engine

Katalyst runs on any of three Ktor server engines: Netty, Jetty, or CIO. You select one in
the bootstrap by passing the engine object to `engine(...)`.

`katalyst-starter-web` already bundles the **Netty** engine, so the default needs no extra
dependency. To run on Jetty or CIO instead, add that engine's module — its version is
managed by the BOM, so you omit the version — and the matching Ktor engine arrives
transitively.

## Select an engine

=== "Netty"

    Bundled in `katalyst-starter-web`; no extra dependency. Just pass the engine object:

    ```kotlin
    import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

    fun main(args: Array<String>) = katalystApplication(args) {
        engine(NettyServer)
        // …
    }
    ```

=== "Jetty"

    ```kotlin
    // build.gradle.kts — version comes from the Katalyst BOM
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-jetty")
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
    // build.gradle.kts — version comes from the Katalyst BOM
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-cio")
    ```

    ```kotlin
    import io.github.darkryh.katalyst.ktor.engine.cio.CioServer

    fun main(args: Array<String>) = katalystApplication(args) {
        engine(CioServer)
        // …
    }
    ```

Each of `NettyServer`, `JettyServer`, and `CioServer` is an object implementing
`KatalystServerEngine`, so swapping engines is a one-line change in the bootstrap (plus the
engine module for non-default engines).

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

