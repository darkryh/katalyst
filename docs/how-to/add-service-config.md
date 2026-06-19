# Add typed service configuration

When a component needs its own configuration — an external API base URL and key, a feature
flag, a messaging endpoint — define a typed config object and let Katalyst load, validate,
and inject it. This guide uses `AutomaticServiceConfigLoader`, the recommended pattern for
component-scoped config.

For infrastructure config that must exist *before* dependency injection starts (the
database, the server port), use the application DSL instead — see
[Configure with YAML](configure-yaml.md). The difference between the two patterns is
explained in [Choosing a configuration pattern](../reference/configuration.md#choosing-a-pattern).

## Step 1: Define the config data class

```kotlin
data class NotificationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 3
)
```

## Step 2: Implement the loader

Implement `AutomaticServiceConfigLoader<T>`. Declare the type it produces, load it from the
`ConfigProvider`, and validate it.

```kotlin
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

object NotificationApiConfigLoader : AutomaticServiceConfigLoader<NotificationApiConfig> {

    override val configType: KClass<NotificationApiConfig> = NotificationApiConfig::class

    override fun loadConfig(provider: ConfigProvider): NotificationApiConfig =
        NotificationApiConfig(
            baseUrl = provider.requiredString("notification.baseUrl"),
            apiKey = provider.requiredString("notification.apiKey"),
            timeoutSeconds = provider.intOrNull("notification.timeoutSeconds") ?: 30,
            retryCount = provider.intOrNull("notification.retryCount") ?: 3
        )

    override fun validate(config: NotificationApiConfig) {
        require(config.baseUrl.isNotBlank()) { "notification.baseUrl is required" }
        require(config.apiKey.isNotBlank()) { "notification.apiKey is required" }
        require(config.timeoutSeconds > 0) { "timeoutSeconds must be > 0" }
        require(config.retryCount >= 0) { "retryCount must be >= 0" }
    }
}
```

The loader must be under a scanned package. The `required*`, `*OrNull`, and `boolean`
helpers come from `katalyst-config-provider`; the full list is in the
[configuration reference](../reference/configuration.md#configloaders-helpers).

## Step 3: Add the YAML

```yaml
notification:
  baseUrl: ${NOTIFICATION_BASE_URL:https://api.notifications.local}
  apiKey: ${NOTIFICATION_API_KEY:dev-api-key}
  timeoutSeconds: ${NOTIFICATION_TIMEOUT_SECONDS:30}
  retryCount: ${NOTIFICATION_RETRY_COUNT:3}
```

## Step 4: Inject the config

Declare the config type as a constructor parameter. Katalyst registers the loaded object as
a singleton, so any service or component receives it by type:

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

## Verify

Start the application. During bootstrap you will see the loader discovered and registered:

```
INFO  ComponentRegistrationOrchestrator - Discovering AutomaticServiceConfigLoader implementations...
INFO  ComponentRegistrationOrchestrator - Discovered 1 automatic config loader(s)
INFO  ComponentRegistrationOrchestrator - Loading configuration for NotificationApiConfig
INFO  ComponentRegistrationOrchestrator - ✓ Registered NotificationApiConfig configuration
```

If a required key is missing or `validate` throws, startup fails immediately with a
`ConfigException` naming the offending key — the misconfiguration never reaches a request.

## Related

- [Configuration reference](../reference/configuration.md) — the loader interfaces, helpers,
  and the pattern-comparison table.
- [Configure with YAML](configure-yaml.md) — infrastructure config and profiles.

