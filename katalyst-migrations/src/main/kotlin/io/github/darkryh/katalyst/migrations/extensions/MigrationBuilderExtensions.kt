package io.github.darkryh.katalyst.migrations.extensions

import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.migrations.feature.MigrationFeature
import io.github.darkryh.katalyst.migrations.options.MigrationOptions

/**
 * Enables Exposed migrations for the current application. This extension lives
 * in the optional `katalyst-migrations` module so it is only available when the
 * dependency is added.
 */
fun KatalystApplicationBuilder.enableMigrations(
    configure: MigrationOptions.() -> Unit = {}
): KatalystApplicationBuilder {
    val options = MigrationOptions().apply(configure)
    return feature(MigrationFeature(options))
}
