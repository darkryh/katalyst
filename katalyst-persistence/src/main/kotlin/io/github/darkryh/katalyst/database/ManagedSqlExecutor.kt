package io.github.darkryh.katalyst.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as ExposedTransactionManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * [SqlExecutor] implementation that uses Katalyst-managed datasource/transactions.
 */
class ManagedSqlExecutor(
    private val dataSource: DataSource
) : SqlExecutor {

    companion object {
        private val logger = LoggerFactory.getLogger(ManagedSqlExecutor::class.java)
    }

    override suspend fun <T> withConnection(block: (Connection) -> T): T {
        val txConnection = currentTransactionConnectionOrNull()
        if (txConnection != null) {
            logger.trace("Using existing transaction-bound JDBC connection")
            return runCatching { block(txConnection) }.getOrElse { throwable ->
                throw wrapError("Managed SQL block failed in transaction context", throwable)
            }
        }

        return withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                try {
                    val result = block(connection)
                    if (!connection.autoCommit) {
                        connection.commit()
                    }
                    result
                } catch (throwable: Throwable) {
                    if (!connection.autoCommit) {
                        runCatching { connection.rollback() }
                            .onFailure { rollbackError ->
                                logger.warn("Managed SQL rollback failed: {}", rollbackError.message, rollbackError)
                            }
                    }
                    throw wrapError("Managed SQL block failed using datasource connection", throwable)
                }
            }
        }
    }

    override suspend fun executeUpdate(sql: String, parameters: List<Any?>): Int = withConnection { connection ->
        runSql(sql) {
            connection.prepareStatement(sql).use { statement ->
                bindParameters(statement, parameters)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun executeBatch(statements: List<String>) {
        if (statements.isEmpty()) return
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql ->
                    runSql(sql) {
                        statement.execute(sql)
                    }
                }
            }
        }
    }

    override suspend fun <T> query(
        sql: String,
        parameters: List<Any?>,
        mapper: (java.sql.ResultSet) -> T
    ): List<T> = withConnection { connection ->
        runSql(sql) {
            connection.prepareStatement(sql).use { statement ->
                bindParameters(statement, parameters)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(mapper(resultSet))
                        }
                    }
                }
            }
        }
    }

    override suspend fun <T> queryOne(
        sql: String,
        parameters: List<Any?>,
        mapper: (java.sql.ResultSet) -> T
    ): T? = query(sql, parameters, mapper).firstOrNull()

    private fun bindParameters(statement: java.sql.PreparedStatement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value ->
            statement.setObject(index + 1, value)
        }
    }

    private inline fun <T> runSql(sql: String, action: () -> T): T {
        return try {
            action()
        } catch (e: SQLException) {
            throw SqlExecutionException("Managed SQL execution failed for [$sql]: ${e.message}", e)
        }
    }

    private fun currentTransactionConnectionOrNull(): Connection? {
        val transaction = ExposedTransactionManager.currentOrNull() ?: return null
        val rawConnection = transaction.connection.connection
        return rawConnection as? Connection
            ?: throw SqlExecutionException(
                "Current Exposed transaction does not expose a JDBC connection (type=${rawConnection::class.qualifiedName})"
            )
    }

    private fun wrapError(message: String, throwable: Throwable): SqlExecutionException {
        return throwable as? SqlExecutionException ?: SqlExecutionException(message, throwable)
    }
}
