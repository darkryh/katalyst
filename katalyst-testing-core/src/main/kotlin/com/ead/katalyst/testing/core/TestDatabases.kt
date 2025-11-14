package com.ead.katalyst.testing.core

import com.ead.katalyst.config.DatabaseConfig
import java.util.UUID

/**
 * Convenience factory for in-memory database configurations used in tests.
 *
 * Creates isolated H2 instances that emulate PostgreSQL behavior so that
 * repositories using Exposed behave the same as they do against Postgres.
 */
fun inMemoryDatabaseConfig(
    name: String = "katalyst-test-${UUID.randomUUID()}"
): DatabaseConfig = DatabaseConfig(
    url = "jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    driver = "org.h2.Driver",
    username = "sa",
    password = ""
)
