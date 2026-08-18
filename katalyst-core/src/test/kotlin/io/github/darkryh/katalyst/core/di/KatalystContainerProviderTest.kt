package io.github.darkryh.katalyst.core.di

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The process-wide container holder.
 *
 * This object is the seam every Katalyst API resolves through, and it had **no test at all** — in a
 * module whose suite is otherwise 2 000 lines of assertions against fakes declared inside the test
 * files. `reset()` in particular is the contract every test harness and the Ktor hot-reload fix
 * depend on, and nothing pinned it.
 */
class KatalystContainerProviderTest {

    private class StubContainer(val name: String) : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            error("not needed for these tests")
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }

    @BeforeTest
    fun setUp() = KatalystContainerProvider.reset()

    @AfterTest
    fun tearDown() = KatalystContainerProvider.reset()

    @Test
    fun `currentOrNull is null before anything is installed`() {
        assertNull(KatalystContainerProvider.currentOrNull())
    }

    @Test
    fun `current returns exactly the installed container`() {
        val container = StubContainer("first")

        KatalystContainerProvider.set(container)

        assertSame(container, KatalystContainerProvider.current())
        assertSame(container, KatalystContainerProvider.currentOrNull())
    }

    @Test
    fun `set replaces a previously installed container`() {
        val first = StubContainer("first")
        val second = StubContainer("second")

        KatalystContainerProvider.set(first)
        KatalystContainerProvider.set(second)

        assertSame(second, KatalystContainerProvider.current(), "the later installation must win")
    }

    @Test
    fun `reset clears the container so a later boot starts clean`() {
        // The property every test harness and the hot-reload path rely on: state must not survive
        // a container lifecycle, or the next bootstrap silently inherits the previous one.
        KatalystContainerProvider.set(StubContainer("first"))

        KatalystContainerProvider.reset()

        assertNull(KatalystContainerProvider.currentOrNull())
    }

    @Test
    fun `reset is idempotent`() {
        KatalystContainerProvider.reset()
        KatalystContainerProvider.reset()

        assertNull(KatalystContainerProvider.currentOrNull())
    }

    @Test
    fun `current fails with an actionable message when nothing is installed`() {
        val error = assertFailsWith<IllegalStateException> { KatalystContainerProvider.current() }

        val message = error.message ?: ""
        // The message is the only guidance a consumer gets at this point, so pin its substance:
        // what went wrong, and the concrete artifact that fixes it.
        assertTrue(message.contains("not initialized"), "must say what is wrong: $message")
        assertTrue(
            message.contains("katalyst-koin-bean"),
            "must name the artifact that resolves it: $message",
        )
    }

    @Test
    fun `current fails again after a reset rather than serving a stale container`() {
        KatalystContainerProvider.set(StubContainer("first"))
        KatalystContainerProvider.reset()

        assertFailsWith<IllegalStateException> { KatalystContainerProvider.current() }
    }

    @Test
    fun `a container installed on one thread is visible to every other thread`() {
        // The field is @Volatile; this pins that it stays so. Without the write barrier a reader
        // thread can keep observing null — a startup race that would present as the "container is
        // not initialized" error under load and vanish under a debugger.
        val container = StubContainer("shared")
        val readers = 16
        val ready = CountDownLatch(readers)
        val go = CountDownLatch(1)
        val seen = AtomicInteger(0)
        val missed = AtomicInteger(0)

        val threads = (1..readers).map {
            thread {
                ready.countDown()
                go.await(10, TimeUnit.SECONDS)
                if (KatalystContainerProvider.currentOrNull() === container) {
                    seen.incrementAndGet()
                } else {
                    missed.incrementAndGet()
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "readers failed to reach the start line")
        KatalystContainerProvider.set(container)
        go.countDown()
        threads.forEach { it.join(10_000) }

        assertEquals(readers, seen.get(), "some threads missed the installed container: ${missed.get()}")
    }

    @Test
    fun `concurrent set and reset always leave a legal state`() {
        // Whatever the interleaving, the holder must hold either a container or nothing — never a
        // torn value that makes current() throw while currentOrNull() reports non-null.
        val container = StubContainer("racy")
        val workers = 8
        val ready = CountDownLatch(workers)
        val go = CountDownLatch(1)
        val illegal = AtomicInteger(0)

        val threads = (1..workers).map { index ->
            thread {
                ready.countDown()
                go.await(10, TimeUnit.SECONDS)
                repeat(500) {
                    if (index % 2 == 0) KatalystContainerProvider.set(container)
                    else KatalystContainerProvider.reset()

                    val observed = KatalystContainerProvider.currentOrNull()
                    if (observed != null && observed !== container) illegal.incrementAndGet()
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS))
        go.countDown()
        threads.forEach { it.join(20_000) }

        assertEquals(0, illegal.get(), "observed a container that was never installed")
    }
}
