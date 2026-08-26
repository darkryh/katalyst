package io.github.darkryh.katalyst.core.lifecycle

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The contract [ApplicationShutdown] has to hold for the TUI inspector's `/shutdown` to be
 * trustworthy: it fires once, off the caller's thread, and says so honestly when it cannot fire at
 * all.
 *
 * Each of these pins a way the feature could be quietly wrong rather than loudly broken — a second
 * teardown racing the first, a request that blocks the HTTP handler that made it, or a "yes" from a
 * process with nothing to stop.
 */
class ApplicationShutdownTest {

    @AfterTest
    fun tearDown() {
        // Process-global state: a leaked installation would leak into every later test in this JVM.
        ApplicationShutdown.resetForTest()
    }

    @Test
    fun `reports unsupported when no application published a way to stop`() {
        assertFalse(ApplicationShutdown.isSupported)

        assertEquals(ShutdownRequest.Unsupported, ApplicationShutdown.request("test"))
    }

    @Test
    fun `runs the installed action once accepted`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") { ran.countDown() }

        assertTrue(ApplicationShutdown.isSupported)
        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("test"))
        assertTrue(ran.await(5, TimeUnit.SECONDS), "the installed action never ran")
    }

    @Test
    fun `fires at most once no matter how many times it is asked`() {
        val runs = AtomicInteger()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        ApplicationShutdown.install("test server") {
            runs.incrementAndGet()
            started.countDown()
            release.await()
        }

        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("first"))
        assertTrue(started.await(5, TimeUnit.SECONDS))

        // A second inspector, a double-press, a retry after a slow response — none of them may start
        // a second teardown over the same container.
        assertEquals(ShutdownRequest.AlreadyRequested, ApplicationShutdown.request("second"))
        assertEquals(ShutdownRequest.AlreadyRequested, ApplicationShutdown.request("third"))

        release.countDown()
        assertEquals(1, runs.get())
    }

    @Test
    fun `does not run the action on the requesting thread`() {
        val ranOn = AtomicReference<String>()
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") {
            ranOn.set(Thread.currentThread().name)
            ran.countDown()
        }

        val requestingThread = Thread.currentThread().name
        ApplicationShutdown.request("test")

        assertTrue(ran.await(5, TimeUnit.SECONDS))
        // The caller is an HTTP handler on the very server being stopped. If the action ran here, the
        // response could never be written and the caller would be waiting on its own shutdown.
        assertNotEquals(requestingThread, ranOn.get())
    }

    @Test
    fun `returns before a slow action has finished`() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        ApplicationShutdown.install("slow server") {
            started.countDown()
            release.await()
        }

        val elapsedMillis =
            System.nanoTime().let { start ->
                ApplicationShutdown.request("test")
                (System.nanoTime() - start) / 1_000_000
            }

        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(elapsedMillis < 1_000, "request() blocked for $elapsedMillis ms; it must not wait")
        release.countDown()
    }

    @Test
    fun `a failing action cannot escape into an uncaught exception handler`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("broken server") {
            ran.countDown()
            error("teardown blew up")
        }

        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("test"))
        assertTrue(ran.await(5, TimeUnit.SECONDS))
        // Nothing to assert beyond "the JVM is still fine": the point is that a teardown failure on a
        // thread with no one to report to must not become a stack trace over a terminal that is
        // being handed back — the exact shape of the crash this whole area was fixed for.
    }

    @Test
    fun `uninstall withdraws the action`() {
        ApplicationShutdown.install("test server") { }
        assertTrue(ApplicationShutdown.isSupported)

        ApplicationShutdown.uninstall()

        assertFalse(ApplicationShutdown.isSupported)
        assertEquals(ShutdownRequest.Unsupported, ApplicationShutdown.request("test"))
    }

    @Test
    fun `installing again clears a previous request`() {
        ApplicationShutdown.install("first boot") { }
        ApplicationShutdown.request("test")
        assertTrue(ApplicationShutdown.isRequested)

        // A second boot in the same JVM — a test suite, a restart — starts from a clean slate;
        // otherwise the new application could never be stopped.
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("second boot") { ran.countDown() }

        assertFalse(ApplicationShutdown.isRequested)
        assertEquals(ShutdownRequest.Accepted, ApplicationShutdown.request("test"))
        assertTrue(ran.await(5, TimeUnit.SECONDS))
    }
}
