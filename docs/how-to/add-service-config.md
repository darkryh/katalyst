# Add typed service configuration

When a component needs its own configuration — an external API base URL and key, a feature
flag, a messaging endpoint — define a typed config object and let Katalyst load, validate,
and inject it. This guide uses `@ConfigPrefix`, the recommended pattern for declarative,
key-per-property component config.

For infrastructure config that must exist *before* dependency injection starts (the
database, the server port), use the application DSL instead — see
[Configure with YAML](configure-yaml.md). The difference between the two patterns is
explained in [Choosing a pattern](../reference/configuration.md#choosing-a-pattern).

## Step 1: Define and annotate the config class

Annotate the class with `@ConfigPrefix` and let each primary-constructor property bind to a
derived key (`prefix.kebab-case(propertyName)`). Put constructor-level validation in `init`:

```kotlin
import io.github.darkryh.katalyst.config.provider.ConfigKey
import io.github.darkryh.katalyst.config.provider.ConfigPrefix

@ConfigPrefix("notification")
data class NotificationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    @ConfigKey("notification.timeout-seconds") val timeoutSeconds: Int = 30,
    val retryCount: Int = 3
) {
    init {
        require(baseUrl.isNotBlank()) { "notification.base-url is required" }
        require(apiKey.isNotBlank()) { "notification.api-key is required" }
        require(timeoutSeconds > 0) { "notification.timeout-seconds must be > 0" }
        require(retryCount >= 0) { "notification.retry-count must be >= 0" }
    }
}
```

`baseUrl` binds to `notification.base-url`, `apiKey` to `notification.api-key`, and
`retryCount` to `notification.retry-count` — each property is `camelCase` converted to
`kebab-case` under the `@ConfigPrefix` value. `timeoutSeconds` uses an explicit `@ConfigKey`
to bind to `notification.timeout-seconds` (equivalent here to the default derivation, but
`@ConfigKey` is how you override the key when the derived name isn't what you want, or to
point at an absolute key outside the prefix).

The class must be under a scanned package (see [`scanPackages`](../reference/application-dsl.md#scanpackages))
— `ConfigBinder` discovers `@ConfigPrefix`-annotated classes during component scanning.

## Step 2: Add the YAML

```yaml
notification:
  base-url: ${NOTIFICATION_BASE_URL:https://api.notifications.local}
  api-key: ${NOTIFICATION_API_KEY:dev-api-key}
  timeout-seconds: ${NOTIFICATION_TIMEOUT_SECONDS:30}
  retry-count: ${NOTIFICATION_RETRY_COUNT:3}
```

## Step 3: Inject the config

Declare the config type as a constructor parameter. Katalyst binds and registers it as a
singleton, so any service or component receives it by type:

```kotlin
class NotificationClient(
    private val config: NotificationApiConfig
) : Service {
    fun send(recipient: String, body: String) {
        val url = "${config.baseUrl}/messages"
        // call the API using config.apiKey, config.timeoutSeconds, config.retryCount
    }
}
```

## When annotations aren't enough

If a config needs imperative logic — parsing, derived defaults, or cross-key validation that
`@ConfigPrefix`/`@ConfigKey` cannot express — implement `ConfigBinding` instead. The primary
constructor must take a single `ConfigProvider` parameter; `ConfigBinder` discovers
implementors the same way, by scanning for subtypes of `ConfigBinding`. This is an
alternative to the `@ConfigPrefix` class from Step 1 — pick one style per config type, not
both:

```kotlin
import io.github.darkryh.katalyst.config.provider.ConfigBinding
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider

class NotificationApiConfig(provider: ConfigProvider) : ConfigBinding {
    val baseUrl: String = provider.requiredString("notification.baseUrl")
    val apiKey: String = provider.requiredString("notification.apiKey")
    val timeoutSeconds: Int = provider.intOrNull("notification.timeoutSeconds") ?: 30
    val retryCount: Int = provider.intOrNull("notification.retryCount") ?: 3

    init {
        require(baseUrl.isNotBlank()) { "notification.baseUrl is required" }
    }
}
```

The `required*` and `*OrNull` helpers used above come from `katalyst-config-provider`; the
full list is in the [configuration reference](../reference/configuration.md#configprovider-read-extensions).

## Verify

Start the application. During bootstrap, Katalyst discovers and binds every `@ConfigPrefix`
and `ConfigBinding` type under your scanned packages and logs how many it found:

```
INFO  ConfigBinder - Discovered 1 configuration type(s)
```

If a required key is missing or an `init { require(...) }` check fails, startup fails
immediately with a `ConfigException` naming the offending type and key — the misconfiguration
never reaches a request.

## Related

- [Configuration reference](../reference/configuration.md) — the binding styles, read
  extensions, and the pattern-comparison table.
- [Configure with YAML](configure-yaml.md) — infrastructure config and profiles.
