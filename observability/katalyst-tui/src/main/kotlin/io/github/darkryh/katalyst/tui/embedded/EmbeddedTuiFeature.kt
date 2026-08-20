package io.github.darkryh.katalyst.tui.embedded

import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.tui.isInteractiveTerminal
import io.github.darkryh.katalyst.tui.runInspectorTui
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Embedded inspector: when `katalyst-tui` is on a backend's runtime classpath, the TUI becomes the
 * default console view. Loaded reflectively by `KatalystApplicationBuilder` — the FQN of this object
 * is a stable contract (`io.github.darkryh.katalyst.tui.embedded.EmbeddedTuiFeature`) and katalyst-di
 * keeps no compile edge to this module.
 *
 * Behavior, decided at boot start:
 *  - **Real TTY** (`java -jar` in a terminal, installDist binary, ssh, IDE with terminal emulation):
 *    console logging is raised to WARN and the Dispatch inspector takes over the screen
 *    immediately — the bootstrap progress view renders the 7 phases as 0→100% while they run,
 *    then flips to the dashboard once boot completes. Quitting the inspector (double Ctrl+C)
 *    shuts the backend down; if boot fails, the process exits and the error prints below the
 *    final frame.
 *  - **No TTY** (IDE Run window, piped output, systemd/nohup service): normal sequential logs,
 *    plus a one-time WARN at the end of boot explaining how to get the TUI.
 *
 * Opt out with `-Dkatalyst.tui.enabled=false` or `KATALYST_TUI_ENABLED=false`. Every step is
 * guarded — an inspector failure can never break the backend.
 */
object EmbeddedTuiFeature : KatalystFeature {

    override val id: String = "tui"

    private val logger = LoggerFactory.getLogger("KatalystTui")

    /** Decided once, as early as possible: will this run hand the terminal to the TUI? */
    @Volatile
    private var takeover = false

    /** Undoes the early log silencing if the TUI never actually takes over. */
    @Volatile
    private var restoreLogLevel: (() -> Unit)? = null

    init {
        // This object is instantiated by KatalystApplicationBuilder's init block (Class.forName),
        // which runs right after the banner prints and BEFORE any boot logging. Deciding takeover
        // here means a TTY run shows exactly: the Katalyst banner, then the TUI's bootstrap
        // progress view — no sequential log preamble at all. WARN/ERROR still print, so a failed
        // boot stays fully visible. The inspector's poll loop tolerates starting before the
        // telemetry transport is up (it just retries), so launching this early is safe.
        runCatching {
            takeover = enabled() && isInteractiveTerminal()
            if (takeover) {
                // The splash renders the Katalyst banner itself; tell the framework not to
                // println it (katalystApplication checks this property before printing).
                runCatching { System.setProperty("katalyst.console.banner", "false") }
                restoreLogLevel = applyQuietMode()
                launchTui()
            }
        }.onFailure { logger.debug("Embedded TUI activation failed: {}", it.message) }
    }

    override fun onReady(context: KatalystBeanContext) {
        if (!enabled() || takeover) return
        logger.warn(
                "\n" +
                    "================================================================================\n" +
                    "The Katalyst TUI inspector is on the classpath, but this console is not a real\n" +
                    "terminal (IDE Run window, piped output, or a service), so the TUI cannot take\n" +
                    "over the screen. Falling back to standard log output. To get the TUI:\n" +
                    "  - run the app from a real terminal: java -jar <app>.jar (or the installDist\n" +
                    "    binary), locally or over ssh, or\n" +
                    "  - IntelliJ: Run configuration -> Modify options -> 'Emulate terminal in\n" +
                    "    output console', or\n" +
                    "  - keep the app running and attach the external inspector from a terminal\n" +
                    "    during development: ./tui.sh\n" +
                    "Silence this notice with -Dkatalyst.tui.enabled=false.\n" +
                    "================================================================================",
        )
    }

    private fun launchTui() {
        runCatching {
            // Daemon thread: if the backend fails at any point (phase failure, port already in
            // use) the main thread dies and the JVM must exit WITH it — a non-daemon renderer
            // would keep a dead backend on screen forever.
            thread(name = "katalyst-tui", isDaemon = true) {
                runCatching {
                    runInspectorTui(
                        preferredPid = runCatching { ProcessHandle.current().pid() }.getOrNull(),
                    )
                }.onFailure {
                    restoreLogLevel?.invoke()
                    logger.warn("Embedded TUI failed; continuing with standard logs: {}", it.message)
                }.onSuccess {
                    if (EmbeddedTuiSession.detachRequested) {
                        // `/exit`: close only the inspector. The backend keeps serving; console
                        // logging returns so the terminal is not left silent.
                        restoreLogLevel?.invoke()
                        logger.info(
                            "Inspector detached — backend keeps running; console logging restored. " +
                                "Reattach anytime from a terminal with ./tui.sh",
                        )
                    } else {
                        // `/shutdown` or Ctrl+C: in embedded mode the TUI is the console, so
                        // quitting it stops the backend (shutdown hooks run: descriptor flips to
                        // STOPPING/STOPPED, server stops).
                        exitProcess(0)
                    }
                }
            }
        }.onFailure {
            restoreLogLevel?.invoke()
            logger.warn("Could not start embedded TUI thread: {}", it.message)
        }
    }

    private fun enabled(): Boolean {
        val raw = runCatching { System.getProperty("katalyst.tui.enabled") }.getOrNull()
            ?: runCatching { System.getenv("KATALYST_TUI_ENABLED") }.getOrNull()
        return !(raw?.equals("false", ignoreCase = true) ?: false)
    }

    /**
     * Same reflective logback raise as telemetry's opt-in quiet mode, but forced here: once this
     * run is committed to the TUI, INFO chatter on stdout belongs to the inspector's Boot tile,
     * not the terminal. Warnings/errors still print; non-logback SLF4J bindings are left
     * untouched. Returns a restore action (back to the previous level) for the failure path.
     */
    private fun applyQuietMode(): (() -> Unit)? = runCatching {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val levelClass = Class.forName("ch.qos.logback.classic.Level")
        val warn = levelClass.getField("WARN").get(null)
        val getLevel = root.javaClass.getMethod("getLevel")
        val setLevel = root.javaClass.getMethod("setLevel", levelClass)
        val previous = getLevel.invoke(root)
        setLevel.invoke(root, warn)
        return@runCatching { runCatching { setLevel.invoke(root, previous) }.let { } }
    }.onFailure { logger.debug("Quiet mode unavailable (non-logback SLF4J binding?): {}", it.message) }
        .getOrNull()
}
