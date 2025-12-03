package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.client.feature.eventSystemFeature
import io.github.darkryh.katalyst.config.yaml.ConfigProviderFeature
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.migrations.feature.MigrationFeature
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import io.github.darkryh.katalyst.websockets.WebSocketFeature

/**
 * Default feature set used by the test environment. Mirrors the production stack
 * so components that depend on scheduler, events, websockets, or config provider
 * behave the same way in tests.
 */
fun defaultTestFeatures(): List<KatalystFeature> = listOf(
    ConfigProviderFeature(),
    eventSystemFeature { withBus(true) },
    SchedulerFeature,
    WebSocketFeature,
    MigrationFeature(MigrationOptions(runAtStartup = false))
)
