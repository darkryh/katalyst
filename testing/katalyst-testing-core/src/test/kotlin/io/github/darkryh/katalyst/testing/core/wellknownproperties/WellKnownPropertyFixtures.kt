package io.github.darkryh.katalyst.testing.core.wellknownproperties

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.scheduler.service.SchedulerService

/**
 * A component that declares the well-known `SchedulerService` property the dependency analyzer
 * reports as satisfied. The framework is supposed to assign it during registration; if it does
 * not, [schedulerOrNull] observes the `lateinit` property as uninitialised - exactly what an
 * application would hit as `UninitializedPropertyAccessException` on first use.
 */
class SchedulerPropertyComponent : Component {

    lateinit var scheduler: SchedulerService

    fun schedulerOrNull(): SchedulerService? =
        if (::scheduler.isInitialized) scheduler else null
}
