package com.ead.katalyst.example.infra.config

import com.ead.katalyst.database.DatabaseConfig

object DatabaseConfigFactory {

    fun fromEnvironment(): DatabaseConfig {
        val jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"

        return DatabaseConfig(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            username = "postgres",
            password = "",
            maxPoolSize = 10,
            minIdleConnections = 1,
            connectionTimeout = 30_000,
            idleTimeout = 600_000,
            maxLifetime = 1_800_000,
            autoCommit = false
        )
    }
}
