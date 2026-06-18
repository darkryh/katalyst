package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.config.provider.boolean
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.config.provider.longOrNull
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.config.provider.stringOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider

/**
 * Database configuration DSL backed by the installed application configuration source.
 *
 * Call [fromConfiguration] to read database.* keys from YAML/properties/etc. and then
 * override individual Hikari/Exposed values in the same DSL block when needed.
 */
class DatabaseConfigurationBuilder internal constructor(
    private val provider: ConfigProvider?,
) {
    var url: String? = null
    var driver: String? = null
    var username: String? = null
    var password: String = ""
    var maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE
    var minIdleConnections: Int = DEFAULT_MIN_IDLE_CONNECTIONS
    var connectionTimeout: Long = DEFAULT_CONNECTION_TIMEOUT
    var idleTimeout: Long = DEFAULT_IDLE_TIMEOUT
    var maxLifetime: Long = DEFAULT_MAX_LIFETIME
    var autoCommit: Boolean = DEFAULT_AUTO_COMMIT
    var transactionIsolation: String = DEFAULT_TRANSACTION_ISOLATION

    /**
     * Direct access to the configured source for custom mapping.
     */
    val configuration: ConfigProvider
        get() = provider ?: throw IllegalStateException(
            "No configuration source configured. Call enableYamlConfiguration() or configuration(customSource) " +
                "before using database { fromConfiguration() }."
        )

    /**
     * Load standard database.* keys from the configured source.
     *
     * Required keys:
     * - database.url
     * - database.driver
     * - database.username
     *
     * Optional keys fall back to conservative Hikari-oriented defaults and can be
     * overwritten after this call inside the same DSL block.
     */
    fun fromConfiguration(prefix: String = "database") {
        val config = configuration
        url = config.requiredString("$prefix.url")
        driver = config.requiredString("$prefix.driver")
        username = config.requiredString("$prefix.username")
        password = config.stringOrNull("$prefix.password").orEmpty()
        maxPoolSize = config.intOrNull("$prefix.pool.maxSize") ?: DEFAULT_MAX_POOL_SIZE
        minIdleConnections = config.intOrNull("$prefix.pool.minIdle") ?: DEFAULT_MIN_IDLE_CONNECTIONS
        connectionTimeout = config.longOrNull("$prefix.pool.connectionTimeout") ?: DEFAULT_CONNECTION_TIMEOUT
        idleTimeout = config.longOrNull("$prefix.pool.idleTimeout") ?: DEFAULT_IDLE_TIMEOUT
        maxLifetime = config.longOrNull("$prefix.pool.maxLifetime") ?: DEFAULT_MAX_LIFETIME
        autoCommit = config.boolean("$prefix.autoCommit", DEFAULT_AUTO_COMMIT)
        transactionIsolation = config.stringOrNull("$prefix.transactionIsolation") ?: DEFAULT_TRANSACTION_ISOLATION
    }

    internal fun build(): DatabaseConfig =
        DatabaseConfig(
            url = requireNotNull(url) {
                "Database URL must be supplied. Set database { url = ... } or call database { fromConfiguration() }."
            },
            driver = requireNotNull(driver) {
                "Database driver must be supplied. Set database { driver = ... } or call database { fromConfiguration() }."
            },
            username = requireNotNull(username) {
                "Database username must be supplied. Set database { username = ... } or call database { fromConfiguration() }."
            },
            password = password,
            maxPoolSize = maxPoolSize,
            minIdleConnections = minIdleConnections,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime,
            autoCommit = autoCommit,
            transactionIsolation = transactionIsolation,
        )

    companion object {
        const val DEFAULT_MAX_POOL_SIZE: Int = 10
        const val DEFAULT_MIN_IDLE_CONNECTIONS: Int = 2
        const val DEFAULT_CONNECTION_TIMEOUT: Long = 30_000L
        const val DEFAULT_IDLE_TIMEOUT: Long = 600_000L
        const val DEFAULT_MAX_LIFETIME: Long = 1_800_000L
        const val DEFAULT_AUTO_COMMIT: Boolean = false
        const val DEFAULT_TRANSACTION_ISOLATION: String = "TRANSACTION_REPEATABLE_READ"
    }
}
