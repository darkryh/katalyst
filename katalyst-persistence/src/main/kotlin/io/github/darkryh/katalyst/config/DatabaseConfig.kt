package io.github.darkryh.katalyst.config

/**
 * Database configuration data class.
 *
 * Holds all configuration needed to establish a database connection
 * via HikariCP connection pool.
 *
 * @param url JDBC connection string (e.g., "jdbc:postgresql://localhost:5432/mydb")
 * @param driver JDBC driver class name (e.g., "org.postgresql.Driver")
 * @param username Database username
 * @param password Database password
 * @param maxPoolSize Maximum number of connections in the pool (default: 10)
 * @param minIdleConnections Minimum idle connections to maintain (default: 2)
 * @param connectionTimeout Connection timeout in milliseconds (default: 30000)
 * @param idleTimeout Idle timeout in milliseconds (default: 600000 = 10 minutes)
 * @param maxLifetime Maximum connection lifetime in milliseconds (default: 1800000 = 30 minutes)
 * @param autoCommit Whether connections auto-commit (default: false for transaction control)
 * @param transactionIsolation Transaction isolation level (default: TRANSACTION_REPEATABLE_READ)
 *
 * **Example:**
 * ```kotlin
 * val config = DatabaseConfig(
 *     url = "jdbc:postgresql://localhost:5432/katalyst_db",
 *     driver = "org.postgresql.Driver",
 *     username = "postgres",
 *     password = "secret",
 *     maxPoolSize = 20
 * )
 * ```
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdleConnections: Int = 2,
    val connectionTimeout: Long = 30000L,
    val idleTimeout: Long = 600000L,
    val maxLifetime: Long = 1800000L,
    val autoCommit: Boolean = false,
    val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ"
) {
    /**
     * init the configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    init {
        require(url.isNotBlank()) { "Database URL cannot be blank" }
        require(driver.isNotBlank()) { "Database driver cannot be blank" }
        require(maxPoolSize > 0) { "Max pool size must be greater than 0" }
        require(minIdleConnections >= 0) { "Min idle connections cannot be negative" }
        require(connectionTimeout > 0) { "Connection timeout must be greater than 0" }
    }
}