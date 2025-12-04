package io.github.darkryh.katalyst.scheduler.job

import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job

/**
 * Type marker for scheduler job results.
 *
 * Represents the return type of scheduler registration methods.
 * This type is used for:
 *
 * 1. **Type Safety:** Methods returning SchedulerJobHandle are explicitly
 *    marked as scheduler registration methods
 *
 * 2. **Discovery:** Framework looks for methods returning SchedulerJobHandle
 *    (not generic Job, avoiding false positives)
 *
 * 3. **Semantic Clarity:** Library users see this type and understand it's
 *    scheduler-related
 *
 * **Relationship to Job:**
 * SchedulerJobHandle IS-A Job - it extends the coroutines Job interface
 * so it can be used wherever Job is expected.
 *
 * **Example Service (Untouched):**
 * ```kotlin
 * class AuthenticationService(...) : Service {
 *     private val scheduler = requireScheduler()
 *
 *     // Framework auto-discovers this at startup via reflection
 *     @Suppress("unused")  // Semantically correct - framework calls this
 *     fun scheduleAuthDigest(): SchedulerJobHandle = scheduler.scheduleCron(
 *         config = ScheduleConfig(taskName = "auth-digest"),
 *         task = { broadcastAuth() },
 *         cronExpression = CronExpression("0 0/1 * * * ?")
 *     )
 * }
 * ```
 *
 * The method signature `() -> SchedulerJobHandle` tells the framework:
 * - This is a scheduler registration method
 * - No parameters to pass
 * - Returns a scheduled job handle
 */
@OptIn(InternalForInheritanceCoroutinesApi::class)
interface SchedulerJobHandle : Job {
    // Extends Job to inherit all cancellation and status methods
    // No additional methods needed - pure marker interface
}

/**
 * Internal implementation wrapper for SchedulerJobHandle.
 *
 * Delegates all Job method calls to the underlying job using
 * Kotlin's delegation mechanism.
 *
 * @param delegate The actual Job from coroutine launch
 */
@OptIn(InternalForInheritanceCoroutinesApi::class)
internal class SchedulerJobHandleImpl(
    private val delegate: Job
) : SchedulerJobHandle, Job by delegate

/**
 * Converts a Job to SchedulerJobHandle.
 *
 * Used internally by SchedulerService methods to mark their
 * return values as scheduler jobs.
 */
internal fun Job.asSchedulerHandle(): SchedulerJobHandle =
    this as? SchedulerJobHandle ?: SchedulerJobHandleImpl(this)
