package io.github.darkryh.katalyst.tui

import androidx.compose.runtime.*
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.layout.Box
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.navigation.NavDisplay
import io.github.darkryh.dispatch.navigation.entryProvider
import io.github.darkryh.dispatch.navigation.popBackStack
import io.github.darkryh.dispatch.navigation.rememberNavBackStack
import io.github.darkryh.dispatch.runtime.DispatchApplication
import io.github.darkryh.dispatch.runtime.ExitKeyBinding
import io.github.darkryh.dispatch.runtime.KeyBindings
import io.github.darkryh.dispatch.runtime.LocalHibernation
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.HorizontalDivider
import io.github.darkryh.katalyst.telemetry.model.PhaseStatus
import io.github.darkryh.katalyst.tui.navigation.*
import io.github.darkryh.katalyst.tui.ui.*
import io.github.darkryh.katalyst.tui.ui.screens.*
import io.github.darkryh.katalyst.tui.viewmodel.AttachStatus
import io.github.darkryh.katalyst.tui.viewmodel.InspectorViewModel
import kotlinx.coroutines.delay
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point for the Katalyst terminal-UI inspector. Boots the Dispatch runtime, holds the
 * app-scoped [InspectorViewModel] (discovery + polling), and renders the chrome + tile navigation.
 */
fun main(args: Array<String>) {
    if (!isInteractiveTerminal()) {
        System.err.println(
            """
            katalyst-tui is a full-screen terminal UI and needs a real interactive terminal (TTY).
            This process's stdin/stdout are redirected (IDE Run window, `gradlew run`, or a pipe),
            so the Dispatch renderer cannot take over the screen here.

            Run it from a real terminal (Terminal.app, iTerm, IntelliJ's Terminal tab, or ssh):
              ./tui.sh
            or manually:
              ./gradlew :katalyst-tui:installDist
              ./katalyst-tui/build/install/katalyst-tui/bin/katalyst-tui

            From the IDE Run button: edit the run configuration -> Modify options ->
            "Emulate terminal in output console", then Run works.

            To validate the discovery/attach chain WITHOUT a TTY (CI, ssh, IDE), run the doctor:
              java -cp "katalyst-tui/build/install/katalyst-tui/lib/*" io.github.darkryh.katalyst.tui.AttachDoctorKt
            """.trimIndent(),
        )
        exitProcess(64)
    }
    runInspectorTui(args)
}

/**
 * Boots the Dispatch inspector UI on the calling thread and blocks until it exits. [preferredPid]
 * pins discovery to that backend — the embedded feature passes its own pid so an in-process
 * inspector always shows its host process, never another backend running on the same machine.
 */
fun runInspectorTui(args: Array<String> = emptyArray(), preferredPid: Long? = null) {
    DispatchApplication(args) {
        config {
            name = "katalyst-tui"
            version = "0.1.0"
            theme = DispatchTheme.Dark
            exitKeys(ExitKeyBinding.ctrl("C"))
            // Full-screen live dashboard: keep the ENTIRE viewport in the in-place active area
            // (clamped to the real terminal height by the render pipeline). With the default 12,
            // everything above the last 12 rows — the chrome included — is committed to append-only
            // scrollback on the first frame and can never repaint, freezing the attach status.
            activeAreaHeight = 500
            hibernation {
                // An unwatched inspector naps fast: 30s without a keypress hibernates (footer
                // shows the ⏾ bar, App pauses the poll so the last frame simply stays put) and
                // any key wakes it instantly. Cache release + GC hint stay at their defaults.
                idleTimeout = 30.seconds
            }
            targetFps = 24
        }
        content { App(preferredPid) }
    }
}

@Composable
fun App(preferredPid: Long? = null) {
    val theme = LocalTheme.current
    val inspector = viewModel { InspectorViewModel(preferredPid) }
    val state by inspector.state.collectAsState()
    val backStack = rememberNavBackStack(HomeRoute)

    // While the attached backend is still booting (health arrives only once boot completes), the
    // terminal belongs to the bootstrap progress view — phases as 0→100%, not sequential logs.
    // Once boot completes, the finished timeline holds for a 5-second countdown before the menu,
    // so a fast boot is still readable. Attaching to an already-running backend (standalone
    // inspector) skips the boot view entirely; Detached keeps the dashboard so its "no backend"
    // state stays visible.
    val bootComplete = state.attach is AttachStatus.Attached &&
        state.snapshot?.health?.bootComplete == true

    // Paced reveal: real phases complete in tens of milliseconds, far faster than the eye. The
    // splash replays them at a human pace — the reveal index trails the real completed-prefix
    // count by one step every 450ms, so every phase visibly runs (spinner) then lands (check
    // mark). Purely presentational: the server itself is never delayed.
    val phases = state.snapshot?.boot?.phases ?: emptyList()
    val actualDone = phases.takeWhile {
        it.status == PhaseStatus.COMPLETED || it.status == PhaseStatus.SKIPPED
    }.count()
    var shownDone by remember { mutableStateOf(0) }
    LaunchedEffect(actualDone) {
        while (shownDone < actualDone) {
            delay(450.milliseconds)
            shownDone++
        }
    }

    var startedDuringBoot by remember { mutableStateOf<Boolean?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(state.attach, bootComplete, shownDone) {
        if (startedDuringBoot == null && state.attach is AttachStatus.Attached) {
            startedDuringBoot = !bootComplete
        }
        // The countdown starts only after the paced reveal has walked every phase.
        val revealDone = startedDuringBoot != true ||
            (phases.isNotEmpty() && shownDone >= phases.size)
        if (bootComplete && revealDone && !showMenu && countdown == null) {
            if (startedDuringBoot == true) {
                for (i in 3 downTo 1) {
                    countdown = i
                    delay(1.seconds)
                }
            }
            showMenu = true
        }
    }
    val showBootstrap = when (state.attach) {
        AttachStatus.Discovering -> true
        is AttachStatus.Attached -> !showMenu
        AttachStatus.Detached -> false
    }

    // Hibernation contract: Dispatch only throttles PAINT when idle — data churn is ours to stop.
    // Pausing the poll freezes the tree, so the renderer no-ops and the last frame stays as-is,
    // with zero snapshot allocation for a screen nobody is watching. Never paused during the
    // bootstrap splash: boot must finish visibly even if the user doesn't touch a key.
    val hibernating = LocalHibernation.current?.isHibernating == true
    LaunchedEffect(hibernating, showMenu) { inspector.paused = hibernating && showMenu }
    if (showBootstrap) {
        BootstrapScreen(state, theme, countdown, shownDone)
        return
    }

    // Deliberately a plain Column, NOT TerminalScreen/Scaffold: those mark everything above the
    // footer as scroll-region content, which the renderer commits to append-only scrollback — a
    // live chrome (attach status, health chips, heap gauge) would freeze on its first frame. A
    // region-less full-height Column + a large activeAreaHeight keeps the whole viewport in the
    // in-place repaint area, which is what a live dashboard needs.
    Column(modifier = Modifier.fillMaxSize()) {
        KatalystBanner(theme)
        Chrome(state, theme)
        HorizontalDivider()

        // Global keys. Escape closes the prompt if focused, else pops the stack (no-op on Home).
        // The command prompt exists ONLY on the dashboard: its Tab toggle and `/` shortcut are
        // UNREGISTERED on subsystem screens so those keys reach the screens' filter tables
        // (bindings consume their key even on a no-op handler). priority = 10: the CommandPalette
        // intercepts Tab (completion) and Escape at priority 0 while visible — the mode toggles
        // must always win over it. Navigation away from Home is only possible with the prompt
        // unfocused (the menu owns Enter), so PaletteState.active is always false off-dashboard.
        val onHome = backStack.lastOrNull() is HomeRoute
        KeyBindings(priority = 10) {
            on(Key.Escape, "back") {
                if (PaletteState.active) PaletteState.active = false else backStack.popBackStack()
            }
        }
        if (onHome) {
            KeyBindings(priority = 10) {
                on(Key.Tab, "focus") {
                    PaletteState.message = null
                    PaletteState.active = !PaletteState.active
                }
            }
            if (!PaletteState.active) {
                KeyBindings {
                    on(Key.char('/'), "commands") {
                        PaletteState.message = null
                        PaletteState.active = true
                    }
                }
            }
        }

        val snapshot = state.snapshot
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            NavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<HomeRoute> { HomeScreen() }
                    entry<BootRoute> { BootScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<WiringRoute> { WiringScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<HttpRoute> { HttpScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<SchedulerRoute> { SchedulerScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<SchedulerJobRoute> { route ->
                        SchedulerJobScreen(snapshot, theme, route.jobName) { backStack.popBackStack() }
                    }
                    entry<PersistenceRoute> { PersistenceScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<TransactionsRoute> { TransactionsScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<MigrationsRoute> { MigrationsScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<EventsRoute> { EventsScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<WebSocketsRoute> { WebSocketsScreen(snapshot, theme) { backStack.popBackStack() } }
                    entry<ConfigRoute> { ConfigScreen(snapshot, theme) { backStack.popBackStack() } }
                },
            )
        }

        HorizontalDivider()
        // The command prompt is a dashboard-only affordance; subsystem screens get the one-line
        // hint footer so their tables keep every key and the extra rows.
        if (onHome) BottomBar(state, theme) else SubFooter(state, theme)
    }
}
