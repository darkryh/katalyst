package io.github.darkryh.katalyst.migrations.runner

import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.internal.MigrationHistoryTable
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

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
        validateMigrations(migrations).throwIfInvalid()

        val historyTable = MigrationHistoryTable(options.schemaTable)

        transaction(databaseFactory.database) {
            SchemaUtils.create(historyTable)
        }

        val applied = loadApplied(historyTable).toMutableMap()

        applyBaseline(historyTable, applied, migrations)

        validateAppliedChecksums(migrations, applied)

        val sorted = migrations
            .sortedWith(migrationComparator)
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

    /**
     * Return the migration state without applying migrations or creating the
     * migration history table.
     */
    fun status(migrations: List<KatalystMigration>): MigrationStatusReport {
        val historyTable = MigrationHistoryTable(options.schemaTable)
        val applied = loadAppliedOrEmpty(historyTable)
        val sourceIds = migrations.map { it.id }.toSet()

        val sourceStatuses = migrations
            .sortedWith(migrationComparator)
            .map { migration ->
                val history = applied[migration.id]
                val state = when {
                    history != null && history.status == STATUS_BASELINED -> MigrationState.BASELINED
                    history != null -> MigrationState.APPLIED
                    !matchesTags(migration) || !matchesTarget(migration) -> MigrationState.FILTERED
                    else -> MigrationState.PENDING
                }
                migration.toStatus(state, history)
            }

        val unknownApplied = applied
            .filterKeys { it !in sourceIds }
            .map { (id, history) ->
                MigrationStatus(
                    id = id,
                    version = null,
                    description = history.description,
                    checksum = history.checksum,
                    tags = parseTags(history.tags),
                    state = MigrationState.UNKNOWN_APPLIED,
                    historyStatus = history.status,
                    executionTimeMs = history.executionTimeMs,
                    executedAtEpochMs = history.executedAtEpochMs,
                )
            }
            .sortedWith(compareBy { it.id })

        return MigrationStatusReport(sourceStatuses + unknownApplied)
    }

    /**
     * Validate source migration definitions and applied checksums without
     * mutating the database.
     */
    fun validateMigrations(migrations: List<KatalystMigration>): MigrationValidationResult {
        val errors = mutableListOf<String>()

        collectMigrationDefinitionErrors(migrations, errors)

        val historyTable = MigrationHistoryTable(options.schemaTable)
        val applied = loadAppliedOrEmpty(historyTable)
        collectAppliedChecksumErrors(migrations, applied, errors)

        return MigrationValidationResult(errors)
    }

    /**
     * Return the migrations that would execute without calling [KatalystMigration.up].
     */
    fun dryRun(migrations: List<KatalystMigration>): MigrationDryRunReport {
        val validation = validateMigrations(migrations)
        validation.throwIfInvalid()
        return MigrationDryRunReport(status(migrations).pending)
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
        return compareMigrationKeys(migration.id, target) <= 0
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

    private fun collectMigrationDefinitionErrors(
        migrations: List<KatalystMigration>,
        errors: MutableList<String>,
    ) {
        val duplicateIds = migrations
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys

        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate migration id(s): ${duplicateIds.joinToString()}"
        }

        val duplicateVersions = migrations
            .groupBy { it.version to it.id }
            .filterValues { it.size > 1 }
            .keys
            .filterNot { (_, id) -> id in duplicateIds }

        if (duplicateVersions.isNotEmpty()) {
            errors += "Duplicate migration version/id pair(s): ${
                duplicateVersions.joinToString { "${it.first}/${it.second}" }
            }"
        }
    }

    private fun validateAppliedChecksums(
        migrations: List<KatalystMigration>,
        applied: Map<String, AppliedMigration>,
    ) {
        val errors = mutableListOf<String>()
        collectAppliedChecksumErrors(migrations, applied, errors)
        check(errors.isEmpty()) {
            errors.joinToString(separator = "\n")
        }
    }

    private fun collectAppliedChecksumErrors(
        migrations: List<KatalystMigration>,
        applied: Map<String, AppliedMigration>,
        errors: MutableList<String>,
    ) {
        val migrationsById = migrations.associateBy { it.id }
        applied.forEach { (id, appliedMigration) ->
            val current = migrationsById[id] ?: return@forEach
            if (appliedMigration.checksum != current.checksum) {
                errors += "Checksum mismatch for migration $id. " +
                    "Database has ${appliedMigration.checksum} but code ships ${current.checksum}"
            }
        }
    }

    private fun loadApplied(table: MigrationHistoryTable): Map<String, AppliedMigration> =
        transaction(databaseFactory.database) {
            table.selectAll()
                .associate { row ->
                    row[table.migrationId] to AppliedMigration(
                        checksum = row[table.checksum],
                        description = row[table.description],
                        executionTimeMs = row[table.executionTimeMs],
                        executedAtEpochMs = row[table.executedAtEpochMs],
                        tags = row[table.tags],
                        status = row[table.status],
                    )
                }
        }

    private fun loadAppliedOrEmpty(table: MigrationHistoryTable): Map<String, AppliedMigration> =
        runCatching { loadApplied(table) }
            .getOrElse { emptyMap() }

    private fun applyBaseline(
        table: MigrationHistoryTable,
        applied: MutableMap<String, AppliedMigration>,
        migrations: List<KatalystMigration>
    ) {
        val baseline = options.baselineVersion ?: return
        val candidates = migrations
            .sortedWith(migrationComparator)
            .filter { compareMigrationKeys(it.id, baseline) <= 0 && applied[it.id] == null }
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

    private fun KatalystMigration.toStatus(
        state: MigrationState,
        history: AppliedMigration?,
    ): MigrationStatus =
        MigrationStatus(
            id = id,
            version = version,
            description = description,
            checksum = checksum,
            tags = tags,
            state = state,
            historyStatus = history?.status,
            executionTimeMs = history?.executionTimeMs,
            executedAtEpochMs = history?.executedAtEpochMs,
        )

    private fun parseTags(value: String?): Set<String> =
        value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private data class AppliedMigration(
        val checksum: String?,
        val description: String? = null,
        val executionTimeMs: Long? = null,
        val executedAtEpochMs: Long? = null,
        val tags: String? = null,
        val status: String? = null,
    )

    private companion object {
        val migrationComparator: Comparator<KatalystMigration> =
            compareBy<KatalystMigration> { it.version }
                .thenComparator { left, right -> compareMigrationKeys(left.id, right.id) }

        fun compareMigrationKeys(left: String, right: String): Int {
            val leftParts = left.split('.', '-', '_')
            val rightParts = right.split('.', '-', '_')
            val maxSize = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until maxSize) {
                val leftPart = leftParts.getOrNull(index)
                val rightPart = rightParts.getOrNull(index)
                if (leftPart == null) return -1
                if (rightPart == null) return 1

                val leftNumber = leftPart.toLongOrNull()
                val rightNumber = rightPart.toLongOrNull()
                val comparison = if (leftNumber != null && rightNumber != null) {
                    leftNumber.compareTo(rightNumber)
                } else {
                    leftPart.compareTo(rightPart)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}
