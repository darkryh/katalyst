package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.config.yaml.YamlConfigurationFeature
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.eventSystemFeature
import io.github.darkryh.katalyst.migrations.feature.MigrationFeature
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import io.github.darkryh.katalyst.testing.core.config.FakeConfigProvider
import io.github.darkryh.katalyst.websockets.WebSocketFeature

/**
 * Toggle set for Katalyst features that are useful in integration tests.
 *
 * Defaults mirror the historical [defaultTestFeatures] behavior. Disable individual
 * features when a test needs deterministic startup without background work.
 */
class KatalystTestFeaturesBuilder {
    var configProvider: Boolean = true
    var events: Boolean = true
    var scheduler: Boolean = true
    var websockets: Boolean = true
    var migrations: Boolean = true
    var migrationOptions: MigrationOptions = MigrationOptions(runAtStartup = false)

    fun disableConfigProvider() = apply {
        configProvider = false
    }

    fun disableEvents() = apply {
        events = false
    }

    fun disableScheduler() = apply {
        scheduler = false
    }

    fun disableWebSockets() = apply {
        websockets = false
    }

    fun disableMigrations() = apply {
        migrations = false
    }

    fun migrations(options: MigrationOptions) = apply {
        migrations = true
        migrationOptions = options
    }

    internal fun build(): List<KatalystFeature> = buildList {
        if (configProvider) add(YamlConfigurationFeature(FakeConfigProvider()))
        if (events) add(eventSystemFeature())
        if (scheduler) add(SchedulerFeature)
        if (websockets) add(WebSocketFeature)
        if (migrations) add(MigrationFeature(migrationOptions))
    }
}

/**
 * Default feature set used by the test environment. Mirrors the production stack
 * so components that depend on scheduler, events, websockets, or config provider
 * behave the same way in tests.
 */
fun defaultTestFeatures(): List<KatalystFeature> =
    KatalystTestFeaturesBuilder().build()

/**
 * Builds the default test feature set with selective feature toggles.
 */
fun defaultTestFeatures(
    configure: KatalystTestFeaturesBuilder.() -> Unit
): List<KatalystFeature> =
    KatalystTestFeaturesBuilder().apply(configure).build()
