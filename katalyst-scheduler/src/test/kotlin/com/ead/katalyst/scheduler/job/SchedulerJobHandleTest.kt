package com.ead.katalyst.scheduler.job

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for SchedulerJobHandle interface and implementation.
 *
 * Tests cover:
 * - Interface contract (extends Job)
 * - SchedulerJobHandleImpl delegation
 * - asSchedulerHandle() extension function
 * - Job lifecycle operations
 * - Practical usage scenarios
 */
class SchedulerJobHandleTest {

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `SchedulerJobHandle should extend Job interface`() {
        val job = Job()
        val handle = job.asSchedulerHandle()
        assertTrue(handle is Job)
        assertTrue(handle is SchedulerJobHandle)
    }

    // ========== IMPLEMENTATION TESTS ==========

    @Test
    fun `SchedulerJobHandleImpl should delegate to underlying job`() = runTest {
        val job = Job()
        val handle = job.asSchedulerHandle()

        assertTrue(handle.isActive)
        assertFalse(handle.isCompleted)
        assertFalse(handle.isCancelled)
    }

    @Test
    fun `SchedulerJobHandleImpl should support cancellation`() = runTest {
        val job = Job()
        val handle = job.asSchedulerHandle()

        handle.cancel()

        assertTrue(handle.isCancelled)
        assertFalse(handle.isActive)
    }

    @Test
    fun `SchedulerJobHandleImpl should complete when job completes`() = runTest {
        val job = Job()
        val handle = job.asSchedulerHandle()

        job.complete()
        job.join()

        assertTrue(handle.isCompleted)
        assertFalse(handle.isActive)
    }

    @Test
    fun `SchedulerJobHandleImpl should propagate cancellation`() = runTest {
        val parentJob = Job()
        val handle = parentJob.asSchedulerHandle()

        val childJob = launch(handle) {
            delay(1000)
        }

        handle.cancel()

        assertTrue(childJob.isCancelled)
    }

    // ========== asSchedulerHandle() EXTENSION TESTS ==========

    @Test
    fun `asSchedulerHandle should convert Job to SchedulerJobHandle`() {
        val job = Job()
        val handle = job.asSchedulerHandle()

        assertNotNull(handle)
        assertTrue(handle is SchedulerJobHandle)
    }

    @Test
    fun `asSchedulerHandle should return same handle if already SchedulerJobHandle`() {
        val job = Job()
        val handle1 = job.asSchedulerHandle()
        val handle2 = handle1.asSchedulerHandle()

        // Should return the same instance
        assertTrue(handle1 === handle2)
    }

    @Test
    fun `asSchedulerHandle should wrap regular Job`() {
        val job = Job()
        val handle = job.asSchedulerHandle()

        // Original job and handle should be linked
        job.cancel()
        assertTrue(handle.isCancelled)
    }

    // ========== JOB LIFECYCLE TESTS ==========

    @Test
    fun `SchedulerJobHandle should support join`() = runTest {
        val job = launch {
            delay(10)
        }
        val handle = job.asSchedulerHandle()

        handle.join()

        assertTrue(handle.isCompleted)
    }

    @Test
    fun `SchedulerJobHandle should support children`() = runTest {
        val handle = Job().asSchedulerHandle()

        val child1 = launch(handle) { delay(10) }
        val child2 = launch(handle) { delay(10) }

        assertEquals(2, handle.children.toList().size)

        handle.cancel()

        assertTrue(child1.isCancelled)
        assertTrue(child2.isCancelled)
    }

    @Test
    fun `SchedulerJobHandle should support invokeOnCompletion`() = runTest {
        var completed = false
        val handle = Job().asSchedulerHandle()

        handle.invokeOnCompletion {
            completed = true
        }

        handle.complete()
        handle.join()

        assertTrue(completed)
    }

    @Test
    fun `SchedulerJobHandle should support cancellation with cause`() = runTest {
        val handle = Job().asSchedulerHandle()
        val cause = CancellationException("Task timeout")

        handle.cancel(cause)

        assertTrue(handle.isCancelled)
        assertTrue(handle.getCancellationException().message?.contains("Task timeout") == true)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical scheduler task lifecycle`() = runTest {
        // Simulate scheduling a task
        val taskJob = launch {
            delay(50)
            // Task work here
        }

        val handle = taskJob.asSchedulerHandle()

        // Task is running
        assertTrue(handle.isActive)

        // Wait for completion
        handle.join()

        // Task completed
        assertTrue(handle.isCompleted)
        assertFalse(handle.isActive)
    }

    @Test
    fun `cancelling scheduled task`() = runTest {
        // Schedule a long-running task
        val taskJob = launch {
            delay(10000)
        }

        val handle = taskJob.asSchedulerHandle()

        // Cancel before completion
        handle.cancel()

        // Verify cancellation
        assertTrue(handle.isCancelled)
    }

    @Test
    fun `multiple scheduled tasks with handles`() = runTest {
        val handles = List(5) {
            val job = launch {
                delay(10)
            }
            job.asSchedulerHandle()
        }

        // All tasks active
        handles.forEach { handle ->
            assertTrue(handle.isActive)
        }

        // Wait for all
        handles.forEach { it.join() }

        // All completed
        handles.forEach { handle ->
            assertTrue(handle.isCompleted)
        }
    }

    @Test
    fun `cancelling parent cancels all scheduled tasks`() = runTest {
        val parent = Job()
        val parentHandle = parent.asSchedulerHandle()

        val tasks = List(3) {
            val job = launch(parentHandle) {
                delay(1000)
            }
            job.asSchedulerHandle()
        }

        // Cancel parent
        parentHandle.cancel()

        // All children cancelled
        tasks.forEach { handle ->
            assertTrue(handle.isCancelled)
        }
    }

    @Test
    fun `scheduled task with error handling`() = runTest {
        val job = launch {
            throw RuntimeException("Task failed")
        }

        val handle = job.asSchedulerHandle()

        // Wait and catch exception
        assertFailsWith<RuntimeException> {
            handle.join()
        }

        // Job completed with exception
        assertTrue(handle.isCompleted)
        assertTrue(handle.isCancelled)
    }

    @Test
    fun `scheduled task cleanup on cancellation`() = runTest {
        var cleanupCalled = false

        val job = launch {
            try {
                delay(10000)
            } finally {
                cleanupCalled = true
            }
        }

        val handle = job.asSchedulerHandle()
        handle.cancel()
        handle.join()

        assertTrue(cleanupCalled)
    }

    @Test
    fun `type marker pattern for discovery`() {
        // This demonstrates the pattern - framework looks for methods returning SchedulerJobHandle
        fun scheduleBackup(): SchedulerJobHandle {
            val job = Job()
            return job.asSchedulerHandle()
        }

        val handle = scheduleBackup()

        // Return type is SchedulerJobHandle (not just Job)
        assertTrue(handle is SchedulerJobHandle)
        assertTrue(handle is Job)
    }
}
