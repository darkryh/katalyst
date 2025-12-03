package io.github.darkryh.katalyst.migrations.feature

import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.migrations.service.SchemaDiffService
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import org.koin.core.Koin
import org.koin.dsl.module
import org.slf4j.LoggerFactory

class MigrationFeature(
    private val options: MigrationOptions
) : KatalystFeature {

    private val logger = LoggerFactory.getLogger(MigrationFeature::class.java)

    override val id: String = "migrations"

    override fun provideModules() = listOf(
        module {
            single { options }
            single { SchemaDiffService(get(), options.scriptDirectory) }
            single { MigrationRunner(get(), options) }
        }
    )

    override fun onKoinReady(koin: Koin) {
        if (!options.runAtStartup) {
            val count = koin.getAll<KatalystMigration>().size
            logger.info(
                "Detected {} migration(s) but runAtStartup=false, skipping automatic execution.",
                count
            )
            return
        }

        val migrations = koin.getAll<KatalystMigration>()
        if (migrations.isEmpty()) {
            logger.info("Migrations feature enabled but no migrations were discovered.")
            return
        }

        logger.info("Running {} migration(s) via Katalyst migrations feature", migrations.size)
        koin.get<MigrationRunner>().runMigrations(migrations)
    }
}
