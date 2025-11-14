package com.ead.katalyst.example.scheduler

import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.service.SchedulerService
import com.ead.katalyst.testing.core.KatalystTestEnvironment
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import com.ead.katalyst.testing.core.katalystTestEnvironment
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
            scan("com.ead.katalyst.example")
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
