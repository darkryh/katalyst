package io.github.darkryh.katalyst.testing.core.configfixtures

import io.github.darkryh.katalyst.config.provider.ConfigPrefix
import io.github.darkryh.katalyst.core.component.Service

/**
 * Fixtures for the end-to-end config-binding bootstrap test. Scanned as a dedicated package so
 * discovery only picks up these types.
 */
@ConfigPrefix("app")
data class AppConfig(
    val name: String = "default",
    val retries: Int = 1,
)

/** A [Service] that injects the bound [AppConfig] by constructor — proves end-to-end wiring. */
class ConfigConsumer(val config: AppConfig) : Service
