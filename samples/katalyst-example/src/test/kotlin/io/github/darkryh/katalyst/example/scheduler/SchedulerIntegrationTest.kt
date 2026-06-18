package io.github.darkryh.katalyst.example.scheduler

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.example.sampleJwtTestConfig
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
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
            config(sampleJwtTestConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    }

    @AfterTest
    fun teardown() {
        environment.close()
    }

    @Test
    fun `scheduler executes fixed rate job`() = runBlocking {
        val completion = CompletableDeferred<Unit>()
        val service = object : Service {
            private val scheduler = requireScheduler()

            fun scheduleTestJob() = scheduler.jobs {
                fixedRate(
                    config = ScheduleConfig(
                        taskName = "scheduler.integration.test",
                        onSuccess = { _, _ -> completion.complete(Unit) },
                        onError = { _, exception, _ ->
                            completion.completeExceptionally(exception)
                            false
                        }
                    ),
                    every = 100.milliseconds,
                ) {
                    // no-op
                }
            }
        }

        val handle = service.scheduleTestJob()

        withTimeout(5_000) {
            completion.await()
        }
        handle.cancel()
    }
}
