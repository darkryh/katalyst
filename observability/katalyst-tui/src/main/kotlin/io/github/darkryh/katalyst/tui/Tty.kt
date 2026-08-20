package io.github.darkryh.katalyst.tui

/**
 * True only when this process is attached to a real interactive terminal. `System.console()` is
 * null whenever stdin/stdout are redirected (IDE Run window, Gradle, pipes) on JDK <= 21; on
 * JDK 22+ a Console exists even when redirected, so `Console.isTerminal()` (reflective, to keep
 * the 21 toolchain compiling) gives the real answer. Shared by the standalone `main` guard and
 * the embedded feature's launch decision.
 */
internal fun isInteractiveTerminal(): Boolean {
    if (System.getenv("TERM").equals("dumb", ignoreCase = true)) return false
    val console = System.console() ?: return false
    return runCatching {
        java.io.Console::class.java.getMethod("isTerminal").invoke(console) as Boolean
    }.getOrDefault(true)
}
