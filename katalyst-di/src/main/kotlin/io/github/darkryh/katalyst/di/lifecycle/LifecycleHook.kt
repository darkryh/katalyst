package io.github.darkryh.katalyst.di.lifecycle

/**
 * What every Katalyst lifecycle hook has in common: a name for diagnostics, and a position relative
 * to its peers.
 *
 * Declared once on a shared supertype because the object that starts something is usually the same
 * object that stops it. [StartupHook], [ReadyHook] and [ShutdownHook] each used to carry their own
 * `id`/`order` defaults, which meant the most ordinary shape in the framework — one class that boots
 * a worker and shuts it down again — had to override both members for no reason other than Kotlin
 * refusing to choose between two identical inherited defaults.
 */
interface LifecycleHook {
    /**
     * Stable identifier for logs and diagnostics.
     */
    val id: String
        get() = this::class.simpleName ?: "LifecycleHook"

    /**
     * Position among sibling hooks of the same kind. Lower values run first on the way up.
     *
     * Shutdown walks the same numbers in reverse, so a hook is torn down before whatever it was
     * ordered after was torn down — the usual "stop in the opposite order you started" rule, without
     * a second number to keep in sync.
     */
    val order: Int
        get() = 0
}
