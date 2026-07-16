package io.github.darkryh.katalyst.testing.core.lifecyclehooks

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records hook execution across a bootstrap so tests can assert what actually ran.
 */
object HookExecutionLog {
    val executed = CopyOnWriteArrayList<String>()

    fun reset() {
        executed.clear()
    }
}

/** An ordinary component used to prove hooks get real constructor injection. */
class HookDependency : Component {
    val marker: String = "injected"
}

/**
 * The case this fix exists for: a hook that implements ONLY [StartupHook].
 * No Component/Service marker.
 */
class BareStartupHook : StartupHook {
    override val id: String = "bare-startup"

    override suspend fun onStartup() {
        HookExecutionLog.executed += id
    }
}

/** A bare [StartupHook] that constructor-injects a component. */
class InjectingStartupHook(private val dependency: HookDependency) : StartupHook {
    override val id: String = "injecting-startup"

    override suspend fun onStartup() {
        HookExecutionLog.executed += "$id:${dependency.marker}"
    }
}

/** A hook that implements ONLY [ReadyHook]. */
class BareReadyHook : ReadyHook {
    override val id: String = "bare-ready"

    override suspend fun onReady() {
        HookExecutionLog.executed += id
    }
}

/** A bare [ReadyHook] that constructor-injects a component. */
class InjectingReadyHook(private val dependency: HookDependency) : ReadyHook {
    override val id: String = "injecting-ready"

    override suspend fun onReady() {
        HookExecutionLog.executed += "$id:${dependency.marker}"
    }
}

/**
 * Backward compatibility: hooks that ALSO implement [Component] must keep working,
 * since that was the only way to wire a hook before lifecycle discovery existed.
 */
class ComponentMarkedStartupHook(private val dependency: HookDependency) : StartupHook, Component {
    override val id: String = "component-marked-startup"

    override suspend fun onStartup() {
        HookExecutionLog.executed += "$id:${dependency.marker}"
    }
}

/** Ordering probe: must run before [LateStartupHook]. */
class EarlyStartupHook : StartupHook {
    override val id: String = "early-startup"
    override val order: Int = -10

    override suspend fun onStartup() {
        HookExecutionLog.executed += id
    }
}

/** Ordering probe: must run after [EarlyStartupHook]. */
class LateStartupHook : StartupHook {
    override val id: String = "late-startup"
    override val order: Int = 10

    override suspend fun onStartup() {
        HookExecutionLog.executed += id
    }
}
