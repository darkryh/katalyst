package com.ead.katalyst.testing.core

import com.ead.katalyst.client.feature.eventSystemFeature
import com.ead.katalyst.config.yaml.ConfigProviderFeature
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.migrations.feature.MigrationFeature
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.scheduler.SchedulerFeature
import com.ead.katalyst.websockets.WebSocketFeature

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
