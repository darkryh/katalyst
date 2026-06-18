package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.get
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Concurrency-correctness test for container resolution.
 *
 * A lazily-created singleton must be instantiated **exactly once** even when many threads resolve
 * it for the first time simultaneously. A barrier releases all threads at the same instant to
 * maximise the race on first access.
 */
class ContainerResolutionConcurrencyTest {

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `concurrent first-access resolves a singleton exactly once`() {
        val instantiations = AtomicInteger(0)
        val koin = startKoin {
            modules(
                module {
                    single {
                        // A little work so concurrent first-access genuinely overlaps.
                        instantiations.incrementAndGet()
                        Thread.sleep(2)
                        HeavyBean()
                    }
                }
            )
        }.koin
        val container = KoinKatalystContainer(koin)

        val threads = 32
        val barrier = CyclicBarrier(threads)
        val resolved = ConcurrentLinkedQueue<HeavyBean>()
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = (0 until threads).map {
                pool.submit {
                    barrier.await() // all threads hit get() together
                    resolved.add(container.get<HeavyBean>())
                }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, instantiations.get(), "singleton was instantiated more than once under race")
        // Every thread must have observed the very same instance.
        assertEquals(1, resolved.distinctBy { System.identityHashCode(it) }.size, "threads saw different instances")
        assertEquals(threads, resolved.size, "a thread failed to resolve the singleton")
    }

    private class HeavyBean
}
