package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/**
 * Shutdown bookkeeping under a genuinely multi-threaded registration burst.
 *
 * `KoinBeanEngine.managedInstances` is the list that decides what gets closed at shutdown. It is
 * written from `registerInstance`, which real boots call from a single thread - but nothing in the
 * type says so, and a lost entry there is a leaked resource that never closes, while a duplicated
 * entry is a bean closed twice. Both are silent.
 *
 * A barrier releases every thread at the same instant so the writes genuinely overlap.
 */
class KoinBeanEngineConcurrentRegistrationTest {

    @BeforeTest
    fun setUp() {
        runCatching { KoinBeanEngine.stop() }
        runCatching { stopKoin() }
        KoinBeanEngine.start(emptyList(), allowOverrides = true)
    }

    @AfterTest
    fun tearDown() {
        runCatching { KoinBeanEngine.stop() }
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `every concurrently registered closeable is closed exactly once`() {
        val threads = 16
        val perThread = 8
        val total = threads * perThread

        val closed = ConcurrentLinkedQueue<CountingProbe>()
        val probes = (0 until total).map { CountingProbe(it, closed) }

        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            (0 until threads).map { thread ->
                pool.submit {
                    barrier.await()
                    (0 until perThread).forEach { slot ->
                        val index = thread * perThread + slot
                        KoinBeanEngine.registerInstance(
                            instance = probes[index],
                            primaryType = CountingProbe::class,
                            qualifier = "probe-$index",
                        )
                    }
                }
            }.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        KoinBeanEngine.stop()

        assertEquals(
            total,
            closed.size,
            "every concurrently registered closeable must be closed exactly once at shutdown",
        )
        assertTrue(
            probes.all { it.closeCount == 1 },
            "no bean may be closed twice or left open: " +
                "${probes.filter { it.closeCount != 1 }.map { it.id to it.closeCount }}",
        )
    }

    private class CountingProbe(
        val id: Int,
        private val closed: ConcurrentLinkedQueue<CountingProbe>,
    ) : AutoCloseable {
        @Volatile
        var closeCount: Int = 0

        override fun close() {
            closeCount++
            closed += this
        }
    }
}
