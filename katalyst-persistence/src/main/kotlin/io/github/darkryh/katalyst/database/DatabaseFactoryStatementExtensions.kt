package io.github.darkryh.katalyst.database

import org.slf4j.Logger
import java.sql.Statement

/**
 * Executes [block] with a managed JDBC [Statement] from this [DatabaseFactory].
 *
 * The statement is automatically closed when [block] completes.
 */
suspend inline fun <T> DatabaseFactory.withStatement(
    autoCommit: Boolean = true,
    crossinline block: (Statement) -> T
): T {
    val sqlExecutor = createSqlExecutor()
    return sqlExecutor.withConnection { connection ->
        connection.autoCommit = autoCommit
        connection.createStatement().use { statement ->
            block(statement)
        }
    }
}

/**
 * Executes [block] with a managed JDBC [Statement] and logs failures as a skip.
 */
suspend inline fun DatabaseFactory.withStatement(
    log: Logger,
    skipLabel: String,
    autoCommit: Boolean = true,
    crossinline block: (Statement) -> Unit
) {
    runCatching {
        withStatement(autoCommit = autoCommit, block = block)
    }.onFailure { error ->
        log.warn("Skipping {}: {}", skipLabel, error.message)
    }
}
