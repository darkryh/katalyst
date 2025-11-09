package com.ead.katalyst.example.infra.config

import com.ead.katalyst.config.DatabaseConfig

private const val ENV_DB_URL = "KATALYST_EXAMPLE_DB_URL"
private const val ENV_DB_USERNAME = "KATALYST_EXAMPLE_DB_USERNAME"
private const val ENV_DB_PASSWORD = "KATALYST_EXAMPLE_DB_PASSWORD"
private const val ENV_DB_DRIVER = "KATALYST_EXAMPLE_DB_DRIVER"
private const val ENV_DB_MAX_POOL = "KATALYST_EXAMPLE_DB_MAX_POOL"
private const val ENV_DB_MIN_IDLE = "KATALYST_EXAMPLE_DB_MIN_IDLE"
private const val ENV_DB_TIMEOUT = "KATALYST_EXAMPLE_DB_TIMEOUT"
private const val ENV_DB_IDLE_TIMEOUT = "KATALYST_EXAMPLE_DB_IDLE_TIMEOUT"
private const val ENV_DB_MAX_LIFETIME = "KATALYST_EXAMPLE_DB_MAX_LIFETIME"

private const val PROP_DB_URL = "katalyst.example.db.url"
private const val PROP_DB_USERNAME = "katalyst.example.db.username"
private const val PROP_DB_PASSWORD = "katalyst.example.db.password"
private const val PROP_DB_DRIVER = "katalyst.example.db.driver"
private const val PROP_DB_MAX_POOL = "katalyst.example.db.maxPool"
private const val PROP_DB_MIN_IDLE = "katalyst.example.db.minIdle"
private const val PROP_DB_TIMEOUT = "katalyst.example.db.connectionTimeout"
private const val PROP_DB_IDLE_TIMEOUT = "katalyst.example.db.idleTimeout"
private const val PROP_DB_MAX_LIFETIME = "katalyst.example.db.maxLifetime"

object DatabaseConfigFactory {

    fun config(): DatabaseConfig {
        val url = resolveValue(ENV_DB_URL, PROP_DB_URL, "jdbc:postgresql://localhost:5432/postgres")
        val username = resolveValue(ENV_DB_USERNAME, PROP_DB_USERNAME, "postgres")
        val password = resolveValue(ENV_DB_PASSWORD, PROP_DB_PASSWORD, "")
        val driver = resolveValue(ENV_DB_DRIVER, PROP_DB_DRIVER, "org.postgresql.Driver")

        return DatabaseConfig(
            url = url,
            driver = driver,
            username = username,
            password = password,
            maxPoolSize = resolveInt(ENV_DB_MAX_POOL, PROP_DB_MAX_POOL, 10),
            minIdleConnections = resolveInt(ENV_DB_MIN_IDLE, PROP_DB_MIN_IDLE, 1),
            connectionTimeout = resolveLong(ENV_DB_TIMEOUT, PROP_DB_TIMEOUT, 30_000L),
            idleTimeout = resolveLong(ENV_DB_IDLE_TIMEOUT, PROP_DB_IDLE_TIMEOUT, 600_000L),
            maxLifetime = resolveLong(ENV_DB_MAX_LIFETIME, PROP_DB_MAX_LIFETIME, 1_800_000L),
            autoCommit = false
        )
    }

    private fun resolveValue(envKey: String, propKey: String, default: String): String =
        System.getProperty(propKey)?.takeIf(String::isNotBlank)
            ?: System.getenv(envKey)?.takeIf(String::isNotBlank)
            ?: default

    private fun resolveInt(envKey: String, propKey: String, default: Int): Int =
        resolveValue(envKey, propKey, default.toString()).toIntOrNull() ?: default

    private fun resolveLong(envKey: String, propKey: String, default: Long): Long =
        resolveValue(envKey, propKey, default.toString()).toLongOrNull() ?: default
}
