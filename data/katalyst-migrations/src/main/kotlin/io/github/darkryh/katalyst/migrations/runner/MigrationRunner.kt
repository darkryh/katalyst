package io.github.darkryh.katalyst.migrations.runner

import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.internal.MigrationHistoryTable
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.telemetry.MigrationTelemetry
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
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

            MigrationTelemetry.begin(migration.id)
            // The history write now happens INSIDE executeMigration, in the migration's own
            // transaction, and therefore inside this try: a bookkeeping failure is a migration
            // failure and is reported as one. Previously recordSuccess sat after the catch, so a
            // failed history write escaped uncaught — after the schema change had already landed.
            val duration = try {
                executeMigration(historyTable, migration)
            } catch (error: Exception) {
                MigrationTelemetry.recordFailure(migration.id, error.message)
                logger.error("{} Migration failed: {}", context, error.message)
                if (migration.blocking && options.stopOnFailure) {
                    throw error
                } else {
                    logger.warn("{} Continuing despite failure (blocking={}, stopOnFailure={})",
                        context, migration.blocking, options.stopOnFailure)
                    return@forEach
                }
            }

            MigrationTelemetry.end(statusChanged = true)
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
        val historyRead = readAppliedHistory(historyTable)
        val applied = historyRead.applied
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
                    checksumCode = null,
                    checksumDb = history.checksum,
                )
            }
            .sortedWith(compareBy { it.id })

        return MigrationStatusReport(
            migrations = sourceStatuses + unknownApplied,
            historyReadable = historyRead.readable,
            historyError = historyRead.error,
        )
    }

    /**
     * Validate source migration definitions and applied checksums without
     * mutating the database.
     */
    fun validateMigrations(migrations: List<KatalystMigration>): MigrationValidationResult {
        val errors = mutableListOf<String>()

        collectMigrationDefinitionErrors(migrations, errors)

        val historyTable = MigrationHistoryTable(options.schemaTable)
        val historyRead = readAppliedHistory(historyTable)
        if (historyRead.readable) {
            collectAppliedChecksumErrors(migrations, historyRead.applied, errors)
        } else {
            errors += "Migration history is unreadable: ${historyRead.error ?: "unknown database error"}"
        }

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

    /**
     * Run one migration and record it, returning how long the body took.
     *
     * For a transactional migration the body and its history row commit **together**, so the pair
     * is all-or-nothing: a crash mid-way rolls both back and the next boot simply re-runs the
     * migration cleanly. A non-transactional migration cannot be made atomic — there is no
     * transaction to enlist the bookkeeping in — so it keeps the older two-step behaviour, and the
     * caller is warned that the window exists.
     */
    private fun executeMigration(
        historyTable: MigrationHistoryTable,
        migration: KatalystMigration,
    ): Long {
        if (!migration.transactional) {
            logger.warn(
                "[{}] transactional=false: the schema change and its history row cannot commit " +
                    "atomically, so a crash between them will leave the migration recorded as " +
                    "un-applied and it will re-run on the next boot",
                migration.id,
            )
            val elapsed = measureTimeMillis { migration.up() }
            recordSuccess(historyTable, migration, elapsed)
            return elapsed
        }

        var elapsed = 0L
        transaction(databaseFactory.database) {
            elapsed = measureTimeMillis { migration.up() }
            writeHistoryRow(historyTable, migration, elapsed)
        }
        return elapsed
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

    private fun readAppliedHistory(table: MigrationHistoryTable): AppliedHistoryRead =
        runCatching { AppliedHistoryRead(applied = loadApplied(table)) }
            .getOrElse { error ->
                // A fresh database legitimately has no history table yet. status()/validate() are
                // read-only and must treat that as an empty, readable history without creating it.
                if (error.isMissingHistoryTable()) {
                    AppliedHistoryRead(applied = emptyMap())
                } else {
                    AppliedHistoryRead(
                        applied = emptyMap(),
                        readable = false,
                        error = error.message ?: error::class.simpleName,
                    )
                }
            }

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

        var historyChanged = false
        candidates.forEach { migration ->
            val executedAt = System.currentTimeMillis()
            try {
                // One transaction per candidate is deliberate. A concurrent process can win the
                // primary-key race; on PostgreSQL that aborts the transaction, so recovery must
                // reload history from a fresh transaction rather than continue in this one.
                transaction(databaseFactory.database) {
                    table.insert {
                        it[migrationId] = migration.id
                        it[checksum] = migration.checksum
                        it[description] = "Baseline: ${migration.description}"
                        it[executionTimeMs] = 0
                        it[executedAtEpochMs] = executedAt
                        it[tags] = migration.tags.joinToString(",")
                        it[status] = STATUS_BASELINED
                    }
                }
                historyChanged = true
                applied[migration.id] = AppliedMigration(
                    checksum = migration.checksum,
                    description = "Baseline: ${migration.description}",
                    executionTimeMs = 0,
                    executedAtEpochMs = executedAt,
                    tags = migration.tags.joinToString(","),
                    status = STATUS_BASELINED,
                )
                logger.info("[{}] Baseline applied (baselineVersion={})", migration.id, baseline)
            } catch (error: Exception) {
                // Treat only a row that now genuinely exists as a concurrent winner. Any other
                // insert failure remains fatal and retains its original cause.
                val concurrent = runCatching { loadApplied(table)[migration.id] }.getOrNull()
                    ?: throw error
                applied[migration.id] = concurrent
                logger.info("[{}] Baseline already recorded by a concurrent runner", migration.id)
            }
        }
        if (historyChanged) MigrationTelemetry.statusChanged()
    }

    private fun recordSuccess(
        table: MigrationHistoryTable,
        migration: KatalystMigration,
        durationMs: Long
    ) {
        transaction(databaseFactory.database) {
            writeHistoryRow(table, migration, durationMs)
        }
    }

    /**
     * Insert the history row **using whatever transaction is already open**.
     *
     * Kept separate from [recordSuccess] so a transactional migration can commit its schema change
     * and its bookkeeping row together. Previously the two were always separate transactions: the
     * body committed, then a second transaction wrote the row, leaving a window in which a crash
     * left the schema changed with no record of it. On the next boot `shouldRun` saw no row,
     * re-ran a non-idempotent `up()`, and the process died on a duplicate object — recoverable
     * only by hand-editing the database.
     */
    private fun writeHistoryRow(
        table: MigrationHistoryTable,
        migration: KatalystMigration,
        durationMs: Long
    ) {
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
            checksumCode = checksum,
            checksumDb = history?.checksum,
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

    private data class AppliedHistoryRead(
        val applied: Map<String, AppliedMigration>,
        val readable: Boolean = true,
        val error: String? = null,
    )

    private fun Throwable.isMissingHistoryTable(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<SQLException>()
            .any {
                // PostgreSQL undefined_table, ANSI/driver table-not-found, and H2's two variants
                // (existing schema vs completely empty database).
                it.sqlState in setOf("42P01", "42S02", "42S04") ||
                    it.errorCode == 42102 || it.errorCode == 42104
            }

    internal companion object {
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
                    compareNatural(leftPart, rightPart)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }

        /**
         * Compare two id segments the way a human reads a version: digit runs numerically, the
         * text between them lexicographically.
         *
         * A plain `String.compareTo` is wrong for the Flyway-style ids this framework itself
         * recommends (`SchemaDiffService` documents `V2__add_primary_key.sql`). `KatalystMigration
         * .version` parses a *leading* numeric prefix, so `V2__…` and `V10__…` both yield
         * `Long.MAX_VALUE` and tie, dropping through to this comparison — where `"V10" < "V2"`
         * character-by-character, and **V10 applied before V2**. Comparing the digit run `10`
         * against `2` as numbers fixes the whole family (`V`, `v`, `R`, or any other prefix)
         * without changing what `version` means for ids that do start with digits.
         */
        fun compareNatural(left: String, right: String): Int {
            var l = 0
            var r = 0
            while (l < left.length && r < right.length) {
                val lDigit = left[l].isDigit()
                val rDigit = right[r].isDigit()
                if (lDigit && rDigit) {
                    val lStart = l
                    val rStart = r
                    while (l < left.length && left[l].isDigit()) l++
                    while (r < right.length && right[r].isDigit()) r++
                    // Compare by value, not width, so `007` and `7` are equal in magnitude. Use the
                    // digit strings with leading zeros stripped to stay safe past Long range.
                    val lNum = left.substring(lStart, l).trimStart('0')
                    val rNum = right.substring(rStart, r).trimStart('0')
                    if (lNum.length != rNum.length) return lNum.length - rNum.length
                    val byValue = lNum.compareTo(rNum)
                    if (byValue != 0) return byValue
                } else {
                    if (left[l] != right[r]) return left[l].compareTo(right[r])
                    l++
                    r++
                }
            }
            return (left.length - l) - (right.length - r)
        }
    }
}
