package io.github.darkryh.katalyst.scheduler.lifecycle

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.exception.SchedulerInvocationException
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.reflect.KFunction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class SchedulerInitializerDiscoveryTest {

    @BeforeTest
    fun setUp() {
        runCatching { stopKoin() }
        startKoin {
            modules(
                module {
                    single { SchedulerInjectedConfig("config-value") }
                }
            )
        }
        ServiceRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        ServiceRegistry.clear()
        stopKoin()
    }

    @Test
    fun `discoverCandidateMethods keeps multiple scheduler methods from the same service`() {
        val initializer = SchedulerInitializer()
        val services = listOf(MultiJobService())

        val candidates = discoverCandidates(initializer, services)
        val methodNames = extractMethodNames(candidates).toSet()

        assertEquals(
            setOf("firstJob", "secondJob"),
            methodNames,
            "All scheduler registration methods from the same service should be retained"
        )
    }

    @Test
    fun `validateCandidatesByBytecode validates all scheduler methods from the same service`() {
        val initializer = SchedulerInitializer()
        val services = listOf(MultiJobService())

        val candidates = discoverCandidates(initializer, services)
        val validated = validateCandidates(initializer, candidates)
        val methodNames = extractMethodNames(validated).toSet()

        assertEquals(
            setOf("firstJob", "secondJob"),
            methodNames,
            "Bytecode validation should preserve all valid methods from a service"
        )
    }

    @Test
    fun `validateCandidatesByBytecode handles Kotlin-mangled scheduler method names`() {
        val initializer = SchedulerInitializer()
        val services = listOf(MangledMethodService())

        val candidates = discoverCandidates(initializer, services)
        val validated = validateCandidates(initializer, candidates)
        val methodNames = extractMethodNames(validated).toSet()

        assertEquals(
            setOf("fixedDelayJob"),
            methodNames,
            "Bytecode validation should accept scheduleFixedDelay JVM-mangled method calls"
        )
    }

    @Test
    fun `runtime ready invocation supports scheduler methods with default parameters`() = runTest {
        val service = DefaultParameterJobService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onRuntimeReady()

        assertEquals(
            listOf("scheduler.test.default-parameter.60"),
            service.scheduledTaskNames
        )
    }

    @Test
    fun `runtime ready invocation injects scheduler method config parameters`() = runTest {
        val service = ConfigParameterJobService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onRuntimeReady()

        assertEquals(
            listOf("scheduler.test.config-parameter.config-value"),
            service.scheduledTaskNames
        )
    }

    @Test
    fun `runtime ready invocation fails clearly for required primitive scheduler parameters`() = runTest {
        val service = RequiredPrimitiveJobService()
        ServiceRegistry.register(service)

        val error = assertFailsWith<SchedulerInvocationException> {
            SchedulerInitializer().onRuntimeReady()
        }

        assertEquals(
            true,
            error.message.orEmpty().contains("intervalSeconds")
        )
    }

    private fun discoverCandidates(
        initializer: SchedulerInitializer,
        services: List<Service>
    ): List<*> {
        val method = SchedulerInitializer::class.java.getDeclaredMethod(
            "discoverCandidateMethods",
            List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(initializer, services) as List<*>
    }

    private fun validateCandidates(
        initializer: SchedulerInitializer,
        candidates: List<*>
    ): List<*> {
        val method = SchedulerInitializer::class.java.getDeclaredMethod(
            "validateCandidatesByBytecode",
            List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(initializer, candidates) as List<*>
    }

    private fun extractMethodNames(candidates: List<*>): List<String> {
        return candidates.mapNotNull { candidate ->
            if (candidate == null) return@mapNotNull null
            val methodField = candidate::class.java.getDeclaredField("method")
            methodField.isAccessible = true
            val method = methodField.get(candidate) as KFunction<*>
            method.name
        }
    }
}

private class MultiJobService : Service {
    private val scheduler = SchedulerService()

    fun firstJob(): SchedulerJobHandle = scheduler.scheduleCron(
        config = ScheduleConfig("scheduler.test.first-job"),
        task = {},
        cronExpression = CronExpression("0 0 * * * ?")
    )

    fun secondJob(): SchedulerJobHandle = scheduler.scheduleCron(
        config = ScheduleConfig("scheduler.test.second-job"),
        task = {},
        cronExpression = CronExpression("0 0 * * * ?")
    )
}

private class MangledMethodService : Service {
    private val scheduler = SchedulerService()

    fun fixedDelayJob(): SchedulerJobHandle = scheduler.scheduleFixedDelay(
        config = ScheduleConfig("scheduler.test.fixed-delay-job"),
        task = {},
        fixedDelay = 1.minutes
    )
}

private class DefaultParameterJobService : Service {
    private val scheduler = SchedulerService()
    val scheduledTaskNames = mutableListOf<String>()

    fun defaultParameterJob(intervalSeconds: Int = 60): SchedulerJobHandle {
        val taskName = "scheduler.test.default-parameter.$intervalSeconds"
        scheduledTaskNames += taskName
        return scheduler.scheduleCron(
            config = ScheduleConfig(taskName),
            task = {},
            cronExpression = CronExpression("0 0 * * * ?")
        )
    }
}

private class ConfigParameterJobService : Service {
    private val scheduler = SchedulerService()
    val scheduledTaskNames = mutableListOf<String>()

    fun configParameterJob(config: SchedulerInjectedConfig): SchedulerJobHandle {
        val taskName = "scheduler.test.config-parameter.${config.value}"
        scheduledTaskNames += taskName
        return scheduler.scheduleCron(
            config = ScheduleConfig(taskName),
            task = {},
            cronExpression = CronExpression("0 0 * * * ?")
        )
    }
}

private class RequiredPrimitiveJobService : Service {
    private val scheduler = SchedulerService()

    fun requiredPrimitiveJob(intervalSeconds: Int): SchedulerJobHandle {
        return scheduler.scheduleCron(
            config = ScheduleConfig("scheduler.test.required-primitive.$intervalSeconds"),
            task = {},
            cronExpression = CronExpression("0 0 * * * ?")
        )
    }
}

private class SchedulerInjectedConfig(val value: String)
