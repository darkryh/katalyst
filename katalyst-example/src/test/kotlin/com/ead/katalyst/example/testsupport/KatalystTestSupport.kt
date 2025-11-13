package com.ead.katalyst.example.testsupport

import com.ead.katalyst.client.feature.eventSystemFeature
import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.config.yaml.ConfigProviderFeature
import com.ead.katalyst.di.config.KatalystDIOptions
import com.ead.katalyst.di.config.initializeKoinStandalone
import com.ead.katalyst.di.config.stopKoinStandalone
import com.ead.katalyst.di.feature.KatalystFeature
import com.ead.katalyst.migrations.feature.MigrationFeature
import com.ead.katalyst.migrations.options.MigrationOptions
import com.ead.katalyst.scheduler.SchedulerFeature
import com.ead.katalyst.websockets.WebSocketFeature
import java.util.UUID
import org.koin.core.Koin

private val DEFAULT_SCAN_PACKAGES = arrayOf("com.ead.katalyst.example")

fun startKatalystForTests(
    databaseConfig: DatabaseConfig = inMemoryDatabaseConfig(),
    scanPackages: Array<String> = DEFAULT_SCAN_PACKAGES,
    features: List<KatalystFeature> = defaultTestFeatures()
): Koin =
    initializeKoinStandalone(
        KatalystDIOptions(
            databaseConfig = databaseConfig,
            scanPackages = scanPackages,
            features = features
        )
    )

fun stopKatalystForTests() = stopKoinStandalone()

fun inMemoryDatabaseConfig(name: String = "katalyst-example-test-${UUID.randomUUID()}"): DatabaseConfig =
    DatabaseConfig(
        url = "jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        username = "sa",
        password = ""
    )

fun defaultTestFeatures(): List<KatalystFeature> = listOf(
    ConfigProviderFeature(),
    eventSystemFeature { withBus(true) },
    SchedulerFeature,
    WebSocketFeature,
    MigrationFeature(MigrationOptions(runAtStartup = false))
)
