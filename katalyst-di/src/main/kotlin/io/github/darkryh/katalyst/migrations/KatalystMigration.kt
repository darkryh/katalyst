package io.github.darkryh.katalyst.migrations

/**
 * Marker interface for Exposed-style database migrations that Katalyst can
 * auto-discover and execute. Keeping this interface in the `katalyst-di`
 * module ensures the scanner can always find migration classes even when the
 * optional runtime feature is not on the classpath.
 */
interface KatalystMigration {
    /** Unique identifier, usually timestamp-based (e.g., `20241009_add_users`). */
    val id: String

    /** Human-readable description shown in logs. */
    val description: String get() = id

    /** Hash used to detect drift between applied migrations and source. */
    val checksum: String get() = id

    /** Version key used for ordering; defaults to parsing a numeric prefix from [id]. */
    val version: Long
        get() = id.takeWhile { it.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE

    /** Environment tags (e.g., `dev`, `prod`, `seed`). */
    val tags: Set<String> get() = emptySet()

    /** Whether a failure should abort bootstrap immediately. */
    val blocking: Boolean get() = true

    /** Whether the migration must run inside a transaction. */
    val transactional: Boolean get() = true

    /** Apply the schema change. */
    fun up()

    /** Optional rollback hook. */
    fun down() {}
}
