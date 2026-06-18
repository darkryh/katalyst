package io.github.darkryh.katalyst.migrations.feature

import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.migrations.service.SchemaDiffService
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import org.slf4j.LoggerFactory

class MigrationFeature(
    private val options: MigrationOptions
) : KatalystFeature {

    private val logger = LoggerFactory.getLogger(MigrationFeature::class.java)

    override val id: String = "migrations"

    override fun provideBeanModules() = listOf(
        katalystBeanModule {
            single { options }
            single { SchemaDiffService(get(), options.scriptDirectory) }
            single { MigrationRunner(get(), options) }
        }
    )

    override fun onReady(context: KatalystBeanContext) {
        if (!options.runAtStartup) {
            val count = context.getAll<KatalystMigration>().size
            logger.info(
                "Detected {} migration(s) but runAtStartup=false, skipping automatic execution.",
                count
            )
            return
        }

        val migrations = context.getAll<KatalystMigration>()
        if (migrations.isEmpty()) {
            logger.info("Migrations feature enabled but no migrations were discovered.")
            return
        }

        logger.info("Running {} migration(s) via Katalyst migrations feature", migrations.size)
        context.get<MigrationRunner>().runMigrations(migrations)
    }
}
