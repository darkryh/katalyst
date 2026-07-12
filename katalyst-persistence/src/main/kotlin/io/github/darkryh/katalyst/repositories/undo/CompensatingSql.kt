package io.github.darkryh.katalyst.repositories.undo

/**
 * Minimal, dependency-free helpers for building compensating INSERT/UPDATE/DELETE
 * statements from operation-log data (a table name plus a column-name -> value map).
 *
 * The undo strategies in this package only have access to what was captured on the
 * [io.github.darkryh.katalyst.transactions.workflow.TransactionOperation]: `resourceType`
 * (used as the table name), `resourceId` (used as the primary key value, conventionally
 * bound to a column named [ID_COLUMN]), and `undoData`/`operationData` (column values).
 * There is no table registry available at this layer, so statements are built generically
 * and executed as raw SQL against the current Exposed transaction.
 *
 * Only scalar values (String, Number, Boolean, null) are supported - anything else is
 * rejected by [isScalar] so callers can fail the undo instead of emitting bad SQL.
 *
 * Identifiers are quoted via the caller-supplied `quoteIdentifier` function, which
 * callers should back with the current transaction's
 * `org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi.quoteIfNecessary`.
 * That mirrors exactly how Exposed itself decides whether to quote a table/column name
 * (case-folding rules and reserved-keyword lists are dialect-specific - e.g. H2 upper-cases
 * unquoted identifiers but quotes reserved words like `name`), so a name built here always
 * resolves to the same physical column/table Exposed's own DDL/DML would target.
 */
internal object CompensatingSql {

    /** Conventional primary key column name used across Katalyst-managed tables. */
    const val ID_COLUMN: String = "id"

    private val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    /** Whether [name] is safe to use as a SQL identifier at all (structure-wise). */
    fun isValidIdentifier(name: String?): Boolean =
        name != null && IDENTIFIER.matches(name)

    /** Whether [value] can be safely rendered as a SQL literal. */
    fun isScalar(value: Any?): Boolean = when (value) {
        null, is String, is Number, is Boolean -> true
        else -> false
    }

    private fun literal(value: Any?): String = when (value) {
        null -> "NULL"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is String -> "'" + value.replace("'", "''") + "'"
        else -> error("Unsupported literal type for compensating SQL: ${value::class}")
    }

    /** Renders a resourceId (always a String) as a numeric or string literal as appropriate. */
    private fun idLiteral(id: String): String =
        if (id.matches(Regex("^-?\\d+$"))) id else literal(id)

    fun insertStatement(table: String, columns: Map<String, Any?>, quoteIdentifier: (String) -> String): String {
        require(columns.isNotEmpty()) { "Cannot build INSERT with no columns" }
        val names = columns.keys.joinToString(", ") { quoteIdentifier(it) }
        val values = columns.values.joinToString(", ") { literal(it) }
        return "INSERT INTO ${quoteIdentifier(table)} ($names) VALUES ($values)"
    }

    fun deleteByIdStatement(
        table: String,
        id: String,
        quoteIdentifier: (String) -> String,
        idColumn: String = ID_COLUMN
    ): String =
        "DELETE FROM ${quoteIdentifier(table)} WHERE ${quoteIdentifier(idColumn)} = ${idLiteral(id)}"

    fun updateByIdStatement(
        table: String,
        id: String,
        columns: Map<String, Any?>,
        quoteIdentifier: (String) -> String,
        idColumn: String = ID_COLUMN
    ): String {
        require(columns.isNotEmpty()) { "Cannot build UPDATE with no columns" }
        val assignments = columns.entries.joinToString(", ") { (name, value) ->
            "${quoteIdentifier(name)} = ${literal(value)}"
        }
        return "UPDATE ${quoteIdentifier(table)} SET $assignments WHERE ${quoteIdentifier(idColumn)} = ${idLiteral(id)}"
    }
}
