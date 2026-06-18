package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as JdbcTxManager
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real-Postgres resilience tests (Phase 2 — failure injection).
 *
 * Proves the transaction manager behaves correctly against a real database (not just H2) and
 * **fails fast and cleanly** when the database disappears, rather than hanging. Entirely
 * Docker-gated: when Docker is unavailable (e.g. some local boxes) the whole class is skipped via
 * a JUnit assumption, so it never breaks a Dockerless build but runs in CI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseTransactionManagerPostgresResilienceTest {

    @BeforeAll
    fun requireDocker() {
        assumeTrue(
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false),
            "Docker not available — skipping Postgres resilience tests"
        )
    }

    @Test
    fun `concurrent transactions against real postgres commit without lost updates`() = runBlocking {
        newPostgres().use { pg ->
            pg.start()
            val manager = DatabaseTransactionManager(connect(pg))
            manager.transaction {
                JdbcTxManager.current().exec("CREATE TABLE counter (id INT PRIMARY KEY, total INT NOT NULL)")
                JdbcTxManager.current().exec("INSERT INTO counter (id, total) VALUES (1, 0)")
            }

            val workers = 8
            val perWorker = 25
            coroutineScope {
                (0 until workers).map {
                    async(Dispatchers.Default) {
                        repeat(perWorker) {
                            manager.transaction {
                                // Postgres row locks serialize this; no lost update.
                                JdbcTxManager.current().exec("UPDATE counter SET total = total + 1 WHERE id = 1")
                            }
                        }
                    }
                }.awaitAll()
            }

            val total = manager.transaction {
                JdbcTxManager.current().exec("SELECT total AS t FROM counter WHERE id = 1") { rs: ResultSet ->
                    if (rs.next()) rs.getInt("t") else -1
                }
            }
            assertEquals(workers * perWorker, total)
        }
    }

    @Test
    fun `transactions fail fast and cleanly once the database is unreachable`() = runBlocking {
        val pg = newPostgres().also { it.start() }
        val manager = DatabaseTransactionManager(connect(pg))

        // Sanity: it works while up.
        manager.transaction { JdbcTxManager.current().exec("SELECT 1") }

        // Inject the failure: the database goes away.
        pg.stop()

        // The next transaction must surface an error within a bounded time — never hang.
        val outcome = withTimeoutOrNull(20_000) {
            runCatching {
                manager.transaction(config = TransactionConfig(retryPolicy = RetryPolicy(maxRetries = 0))) {
                    JdbcTxManager.current().exec("SELECT 1")
                }
            }
        }
        assertTrue(outcome != null, "transaction hung after the database became unreachable")
        assertTrue(outcome!!.isFailure, "expected a clean failure once the database was down")
    }

    private fun connect(pg: PostgreSQLContainer<*>): Database =
        Database.connect(
            url = pg.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = pg.username,
            password = pg.password
        )

    companion object {
        // Reuse the locally-cached pgvector image as a Postgres substitute to avoid a network pull.
        private val POSTGRES_IMAGE: DockerImageName =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")

        private fun newPostgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("katalyst")
                .withUsername("katalyst")
                .withPassword("katalyst")
    }
}
