package com.ead.katalyst.migrations.runner

import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.migrations.KatalystMigration
import com.ead.katalyst.migrations.internal.MigrationHistoryTable
import com.ead.katalyst.migrations.options.MigrationOptions
import kotlin.system.measureTimeMillis
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private const val STATUS_SUCCESS = "SUCCESS"
private const val STATUS_BASELINED = "BASELINED"

class MigrationRunner(
    private val databaseFactory: DatabaseFactory,
    private val options: MigrationOptions
) {

    private val logger = LoggerFactory.getLogger(MigrationRunner::class.java)

    fun runMigrations(migrations: List<KatalystMigration>) {
        if (migrations.isEmpty()) {
            logger.info("No migrations discovered")
            return
        }

        val historyTable = MigrationHistoryTable(options.schemaTable)

        transaction(databaseFactory.database) {
            SchemaUtils.create(historyTable)
        }

        val applied = loadApplied(historyTable).toMutableMap()

        applyBaseline(historyTable, applied, migrations)

        val sorted = migrations
            .sortedWith(compareBy<KatalystMigration> { it.order }.thenBy { it.id })
            .filter { matchesTags(it) }
            .filter { matchesTarget(it) }
            .filter { shouldRun(it, applied) }

        if (sorted.isEmpty()) {
            logger.info("No pending migrations after filtering (includeTags={}, excludeTags={})",
                options.includeTags, options.excludeTags
            )
            return
        }

        logger.info("Executing {} pending migration(s)", sorted.size)

        sorted.forEach { migration ->
            val context = "[${migration.id}]"
            if (options.dryRun) {
                logger.info("Dry-run {} would execute migration (tags={})", context, migration.tags)
                return@forEach
            }

            logger.info("{} Starting migration (tags={}, blocking={})", context, migration.tags, migration.blocking)

            val duration = try {
                measureTimeMillis {
                    executeMigration(migration)
                }
            } catch (error: Exception) {
                logger.error("{} Migration failed: {}", context, error.message)
                if (migration.blocking && options.stopOnFailure) {
                    throw error
                } else {
                    logger.warn("{} Continuing despite failure (blocking={}, stopOnFailure={})",
                        context, migration.blocking, options.stopOnFailure)
                    return@forEach
                }
            }

            recordSuccess(historyTable, migration, duration)
            applied[migration.id] = AppliedMigration(migration.checksum)
            logger.info("{} Completed in {} ms", context, duration)
        }
    }

    private fun executeMigration(migration: KatalystMigration) {
        if (migration.transactional) {
            transaction(databaseFactory.database) {
                migration.up()
            }
        } else {
            migration.up()
        }
    }

    private fun matchesTags(migration: KatalystMigration): Boolean {
        if (options.includeTags.isNotEmpty() && migration.tags.intersect(options.includeTags).isEmpty()) {
            logger.debug("[{}] Skipping due to includeTags filter {}", migration.id, options.includeTags)
            return false
        }
        if (options.excludeTags.isNotEmpty() && migration.tags.any { it in options.excludeTags }) {
            logger.debug("[{}] Skipping due to excludeTags filter {}", migration.id, options.excludeTags)
            return false
        }
        return true
    }

    private fun matchesTarget(migration: KatalystMigration): Boolean {
        val target = options.targetVersion ?: return true
        return migration.id <= target
    }

    private fun shouldRun(
        migration: KatalystMigration,
        applied: Map<String, AppliedMigration>
    ): Boolean {
        val alreadyApplied = applied[migration.id] ?: return true
        if (alreadyApplied.checksum != migration.checksum) {
            error(
                "Checksum mismatch for migration ${migration.id}. " +
                    "Database has ${alreadyApplied.checksum} but code ships ${migration.checksum}"
            )
        }
        logger.debug("[{}] Already applied – skipping", migration.id)
        return false
    }

    private fun loadApplied(table: MigrationHistoryTable): Map<String, AppliedMigration> =
        transaction(databaseFactory.database) {
            table.selectAll()
                .associate { row ->
                    row[table.migrationId] to AppliedMigration(row[table.checksum])
                }
        }

    private fun applyBaseline(
        table: MigrationHistoryTable,
        applied: MutableMap<String, AppliedMigration>,
        migrations: List<KatalystMigration>
    ) {
        val baseline = options.baselineVersion ?: return
        val candidates = migrations.filter { it.id <= baseline && applied[it.id] == null }
        if (candidates.isEmpty()) return

        transaction(databaseFactory.database) {
            candidates.forEach { migration ->
                table.insertIgnore {
                    it[migrationId] = migration.id
                    it[checksum] = migration.checksum
                    it[description] = "Baseline: ${migration.description}"
                    it[executionTimeMs] = 0
                    it[executedAtEpochMs] = System.currentTimeMillis()
                    it[tags] = migration.tags.joinToString(",")
                    it[status] = STATUS_BASELINED
                }
                applied[migration.id] = AppliedMigration(migration.checksum)
                logger.info("[{}] Baseline applied (baselineVersion={})", migration.id, baseline)
            }
        }
    }

    private fun recordSuccess(
        table: MigrationHistoryTable,
        migration: KatalystMigration,
        durationMs: Long
    ) {
        transaction(databaseFactory.database) {
            table.insert {
                it[migrationId] = migration.id
                it[checksum] = migration.checksum
                it[description] = migration.description
                it[executionTimeMs] = durationMs
                it[executedAtEpochMs] = System.currentTimeMillis()
                it[tags] = migration.tags.joinToString(",").ifBlank { null }
                it[status] = STATUS_SUCCESS
            }
        }
    }

    private data class AppliedMigration(val checksum: String?)
}
