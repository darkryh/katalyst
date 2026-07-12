package io.github.darkryh.katalyst.scanner.scanner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private interface Widget

private class Widget00 : Widget
private class Widget01 : Widget
private class Widget02 : Widget
private class Widget03 : Widget
private class Widget04 : Widget
private class Widget05 : Widget
private class Widget06 : Widget
private class Widget07 : Widget
private class Widget08 : Widget
private class Widget09 : Widget
private class Widget10 : Widget
private class Widget11 : Widget
private class Widget12 : Widget
private class Widget13 : Widget
private class Widget14 : Widget
private class Widget15 : Widget

/**
 * Verifies the thread-safety contract documented on [InMemoryDiscoveryRegistry]:
 * concurrent registration from multiple threads must not lose entries or throw.
 */
class InMemoryDiscoveryRegistryConcurrencyTest {

    private val allWidgets: List<Class<out Widget>> = listOf(
        Widget00::class.java, Widget01::class.java, Widget02::class.java, Widget03::class.java,
        Widget04::class.java, Widget05::class.java, Widget06::class.java, Widget07::class.java,
        Widget08::class.java, Widget09::class.java, Widget10::class.java, Widget11::class.java,
        Widget12::class.java, Widget13::class.java, Widget14::class.java, Widget15::class.java
    )

    @Test
    fun `concurrent registration from multiple threads does not lose entries or throw`() {
        val registry = InMemoryDiscoveryRegistry(Widget::class.java)
        val threadCount = 8
        val slices = allWidgets.chunked((allWidgets.size + threadCount - 1) / threadCount)

        val readyLatch = CountDownLatch(slices.size)
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(slices.size)

        try {
            val futures = slices.map { slice ->
                executor.submit {
                    readyLatch.countDown()
                    startLatch.await()
                    // Hammer the shared registry with interleaved writes and reads from
                    // every thread to maximize the chance of exposing a race condition.
                    repeat(200) {
                        slice.forEach { widget ->
                            registry.register(widget)
                            registry.isRegistered(widget)
                            registry.getAll()
                            registry.size()
                        }
                    }
                }
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Worker threads failed to start in time")
            startLatch.countDown()

            // Future#get rethrows any exception raised on the worker thread, proving
            // that concurrent registration does not throw.
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        // Proving that concurrent registration does not lose entries.
        assertEquals(allWidgets.size, registry.size())
        assertEquals(allWidgets.toSet(), registry.getAll())
        allWidgets.forEach { widget ->
            assertTrue(registry.isRegistered(widget))
            assertEquals(widget, registry.getByName(widget.simpleName))
        }
    }
}
