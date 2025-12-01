package com.ead.katalyst.di.config

/**
 * Thread-local storage for parsed [BootstrapArgs] so engine factories can
 * access CLI information without requiring args to be threaded through
 * every call site.
 */
object BootstrapArgsHolder {
    private val holder = ThreadLocal<BootstrapArgs?>()

    fun set(args: BootstrapArgs?) {
        if (args == null) {
            holder.remove()
        } else {
            holder.set(args)
        }
    }

    fun current(): BootstrapArgs? = holder.get()

    fun clear() {
        holder.remove()
    }
}
