package io.github.darkryh.katalyst.example.scheduler

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

class SchedulerIntegrationTest {
    private lateinit var environment: KatalystTestEnvironment

    @BeforeTest
    fun setup() {
        environment = katalystTestEnvironment {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    }

    @AfterTest
    fun teardown() {
        environment.close()
    }

    @Test
    fun `scheduler executes fixed rate job`() = runBlocking {
        val scheduler = environment.get<SchedulerService>()
        val completion = CompletableDeferred<Unit>()

        val handle = scheduler.schedule(
            config = ScheduleConfig(
                taskName = "scheduler.integration.test",
                onSuccess = { _, _ -> completion.complete(Unit) },
                onError = { _, exception, _ ->
                    completion.completeExceptionally(exception)
                    false
                }
            ),
            task = { /* no-op */ },
            fixedRate = 100.milliseconds
        )

        withTimeout(5_000) {
            completion.await()
        }
        handle.cancel()
    }
}
