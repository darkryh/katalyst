package io.github.darkryh.katalyst.scheduler.lifecycle

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.scheduler.TestSchedulerContainer
import io.github.darkryh.katalyst.scheduler.exception.SchedulerInvocationException
import io.github.darkryh.katalyst.scheduler.extension.ServiceScheduler
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the documented discovery contract: a public method on a [Service] returning a
 * [SchedulerJobHandle] is invoked at runtime-ready, whatever shape it has (inherited,
 * delegating, suspending) - and anything the framework decides to skip is reported loudly
 * instead of vanishing.
 *
 * All cron fixtures use far-future expressions (03:00-07:00 on 1 January) so no job body ever
 * runs during the suite.
 */
class SchedulerInitializerDiscoveryContractTest {

    private val initializerLogger = LoggerFactory.getLogger("SchedulerInitializer") as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        KatalystContainerProvider.set(
            TestSchedulerContainer(
                mapOf(SchedulerService::class to SchedulerService())
            )
        )
        ServiceRegistry.clear()

        previousLevel = initializerLogger.level
        initializerLogger.level = Level.DEBUG
        appender.list.clear()
        appender.start()
        initializerLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        initializerLogger.detachAppender(appender)
        appender.stop()
        appender.list.clear()
        initializerLogger.level = previousLevel

        ServiceRegistry.clear()
        KatalystContainerProvider.reset()
    }

    @Test
    fun `a scheduler method inherited from a base service is discovered and invoked`() = runTest {
        val service = InheritingSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertEquals(
            listOf("inheritedJob"),
            service.invocations,
            "a scheduler method declared on a base class must still be discovered and invoked"
        )
        assertEquals(1, registrationCount(), summaryMessage())
    }

    @Test
    fun `a scheduler method that delegates registration to a helper is discovered and invoked`() = runTest {
        val service = DelegatingSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertTrue(
            service.delegatingJobInvoked,
            "a scheduler method that registers through a helper must still be invoked"
        )
        assertEquals(1, registrationCount(), summaryMessage())
        assertFalse(
            warnings().any { it.contains("buildJobs") },
            "the helper a valid scheduler method delegates to is not a rejected candidate, got ${warnings()}"
        )
    }

    @Test
    fun `an internal scheduler method is discovered despite JVM name mangling`() = runTest {
        val service = InternalSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertTrue(service.invoked, "an internal method's mangled JVM name must still resolve")
        assertEquals(1, registrationCount(), summaryMessage())
    }

    @Test
    fun `registration reached through the deepest allowed delegation chain is discovered`() = runTest {
        val service = DeepestAllowedDelegationService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertTrue(service.invoked, "three delegating calls are within the limit")
        assertEquals(1, registrationCount(), summaryMessage())
    }

    @Test
    fun `registration hidden behind more delegation than the limit allows is rejected`() = runTest {
        val service = TooDeepDelegationService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertFalse(service.invoked, "a candidate past the delegation limit must not be invoked")
        assertTrue(
            warnings().any { it.contains("tooDeepJob") && it.contains("does not reach") },
            "the over-deep candidate must be reported at WARN, got ${warnings()}"
        )
        assertEquals(0, registrationCount(), summaryMessage())
    }

    @Test
    fun `a delegation cycle terminates and is rejected`() = runTest {
        val service = CyclicDelegationService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertFalse(service.invoked, "a cyclic candidate never reaches the scheduler")
        assertTrue(
            warnings().any { it.contains("cyclicJob") && it.contains("does not reach") },
            "the cyclic candidate must be reported at WARN, got ${warnings()}"
        )
    }

    @Test
    fun `a method that never reaches the scheduler DSL is rejected loudly without failing startup`() = runTest {
        val service = NonSchedulingService()
        ServiceRegistry.register(service)

        // Must not throw: a rejection is a counted skip, never a startup failure.
        SchedulerInitializer().onReady()

        assertFalse(service.invoked, "a rejected candidate must never be invoked")
        assertTrue(
            warnings().any {
                it.contains("NonSchedulingService") &&
                    it.contains("looksLikeAJob") &&
                    it.contains("does not reach")
            },
            "the rejection must be reported at WARN with service, method and reason, got ${warnings()}"
        )
        assertEquals(1, rejectionCount(), summaryMessage())
        assertEquals(0, registrationCount(), summaryMessage())
    }

    @Test
    fun `a private scheduler method is rejected loudly without failing startup`() = runTest {
        val service = PrivateSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertFalse(service.invoked, "a private scheduler method is not part of the contract")
        assertTrue(
            warnings().any {
                it.contains("PrivateSchedulerService") &&
                    it.contains("privateJob") &&
                    it.contains("private")
            },
            "the private-method rejection must be reported at WARN, got ${warnings()}"
        )
        assertEquals(1, rejectionCount(), summaryMessage())
    }

    @Test
    fun `a suspend scheduler method is invoked successfully`() = runTest {
        val service = SuspendSchedulerService()
        ServiceRegistry.register(service)

        SchedulerInitializer().onReady()

        assertTrue(service.invoked, "a suspend scheduler method must be invoked, not aborted")
        assertEquals(1, registrationCount(), summaryMessage())
    }

    @Test
    fun `a genuine invocation failure reports the cause text instead of null`() = runTest {
        val service = NullMessageFailureService()
        ServiceRegistry.register(service)

        val error = assertFailsWith<SchedulerInvocationException> {
            SchedulerInitializer().onReady()
        }

        val message = error.message.orEmpty()
        assertTrue(
            message.contains("IllegalStateException"),
            "the aggregate failure must carry the cause text, got: $message"
        )
        assertFalse(
            message.contains("(): null"),
            "the aggregate failure must never degrade to a literal null, got: $message"
        )
    }

    private fun warnings(): List<String> =
        appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun summaryMessage(): String =
        appender.list
            .lastOrNull { it.formattedMessage.startsWith("Scheduler initialization completed") }
            ?.formattedMessage
            ?: "no scheduler summary line was logged"

    private fun countIn(summary: String, unit: String): Int =
        Regex("(\\d+) ${Regex.escape(unit)}").find(summary)?.groupValues?.get(1)?.toInt() ?: -1

    private fun registrationCount(): Int = countIn(summaryMessage(), "registration(s)")

    private fun rejectionCount(): Int = countIn(summaryMessage(), "rejection(s)")
}

private abstract class BaseSchedulerService : Service {
    protected val scheduler: ServiceScheduler = requireScheduler()
    val invocations = mutableListOf<String>()

    fun inheritedJob(): SchedulerJobHandle {
        invocations += "inheritedJob"
        return scheduler.jobs {
            cron("scheduler.test.inherited", "0 0 3 1 1 ?") {}
        }
    }
}

private class InheritingSchedulerService : BaseSchedulerService()

private class DelegatingSchedulerService : Service {
    private val scheduler = requireScheduler()
    var delegatingJobInvoked = false

    fun delegatingJob(): SchedulerJobHandle {
        delegatingJobInvoked = true
        return buildJobs()
    }

    private fun buildJobs(): SchedulerJobHandle = scheduler.jobs {
        cron("scheduler.test.delegating", "0 0 4 1 1 ?") {}
    }
}

/** An `internal` method: the compiler mangles its JVM name with the module it belongs to. */
private class InternalSchedulerService : Service {
    private val scheduler = requireScheduler()
    var invoked = false

    internal fun internalJob(): SchedulerJobHandle {
        invoked = true
        return scheduler.jobs {
            cron("scheduler.test.internal", "0 0 10 1 1 ?") {}
        }
    }
}

/** Three delegating calls: `deepJob -> first -> second -> third`, the deepest chain allowed. */
private class DeepestAllowedDelegationService : Service {
    private val scheduler = requireScheduler()
    var invoked = false

    fun deepJob(): SchedulerJobHandle {
        invoked = true
        return first()
    }

    private fun first(): SchedulerJobHandle = second()

    private fun second(): SchedulerJobHandle = third()

    private fun third(): SchedulerJobHandle = scheduler.jobs {
        cron("scheduler.test.deep-delegation", "0 0 8 1 1 ?") {}
    }
}

/** One hop too many: the DSL sits behind four calls, so the candidate is not proven. */
private class TooDeepDelegationService : Service {
    private val scheduler = requireScheduler()
    var invoked = false

    fun tooDeepJob(): SchedulerJobHandle {
        invoked = true
        return first()
    }

    private fun first(): SchedulerJobHandle = second()

    private fun second(): SchedulerJobHandle = third()

    private fun third(): SchedulerJobHandle = fourth()

    private fun fourth(): SchedulerJobHandle = scheduler.jobs {
        cron("scheduler.test.too-deep-delegation", "0 0 9 1 1 ?") {}
    }
}

/** `cyclicJob -> ping -> pong -> ping`: the visited set is what stops the walk. */
private class CyclicDelegationService : Service {
    var invoked = false

    fun cyclicJob(): SchedulerJobHandle {
        invoked = true
        return ping()
    }

    private fun ping(): SchedulerJobHandle = pong()

    private fun pong(): SchedulerJobHandle = ping()
}

private class NonSchedulingService : Service {
    var invoked = false

    fun looksLikeAJob(): SchedulerJobHandle {
        invoked = true
        error("a candidate that never reaches the scheduler DSL must not be invoked")
    }
}

private class PrivateSchedulerService : Service {
    private val scheduler = requireScheduler()
    var invoked = false

    @Suppress("unused")
    private fun privateJob(): SchedulerJobHandle {
        invoked = true
        return scheduler.jobs {
            cron("scheduler.test.private", "0 0 5 1 1 ?") {}
        }
    }
}

private class SuspendSchedulerService : Service {
    private val scheduler = requireScheduler()
    var invoked = false

    suspend fun suspendJob(): SchedulerJobHandle {
        yield()
        invoked = true
        return scheduler.jobs {
            cron("scheduler.test.suspend", "0 0 6 1 1 ?") {}
        }
    }
}

private class NullMessageFailureService : Service {
    private val scheduler = requireScheduler()

    fun failingJob(): SchedulerJobHandle {
        scheduler.jobs {
            cron("scheduler.test.null-message", "0 0 7 1 1 ?") {}
        }
        // A cause with no message at all: the aggregate must still say what went wrong.
        throw IllegalStateException()
    }
}
