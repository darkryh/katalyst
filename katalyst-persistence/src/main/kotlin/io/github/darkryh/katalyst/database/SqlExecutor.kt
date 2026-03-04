package io.github.darkryh.katalyst.database

import java.sql.Connection
import java.sql.ResultSet

/**
 * Managed SQL execution API for bootstrap and custom JDBC scenarios.
 *
 * Implementations must use Katalyst-managed datasource/transaction lifecycles.
 */
interface SqlExecutor {

    /**
     * Executes [block] with a managed JDBC [Connection].
     *
     * If an Exposed transaction is active, the same transaction-bound connection is reused.
     * Otherwise, a pooled datasource connection is opened for this block and closed automatically.
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T

    /**
     * Executes a SQL update/DDL statement and returns affected row count.
     */
    suspend fun executeUpdate(sql: String, parameters: List<Any?> = emptyList()): Int

    /**
     * Executes multiple SQL statements within one managed connection context.
     *
     * Useful for startup preconditions/bootstrap DDL where several statements must run together.
     */
    suspend fun executeBatch(statements: List<String>)

    /**
     * Executes a SQL query and maps each row using [mapper].
     */
    suspend fun <T> query(
        sql: String,
        parameters: List<Any?> = emptyList(),
        mapper: (ResultSet) -> T
    ): List<T>

    /**
     * Executes a SQL query and returns the first mapped row, or null when no rows exist.
     */
    suspend fun <T> queryOne(
        sql: String,
        parameters: List<Any?> = emptyList(),
        mapper: (ResultSet) -> T
    ): T?
}
