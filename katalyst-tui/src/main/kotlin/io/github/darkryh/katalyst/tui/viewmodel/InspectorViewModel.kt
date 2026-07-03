package io.github.darkryh.katalyst.tui.viewmodel

import io.github.darkryh.dispatch.viewmodel.StateViewModel
import io.github.darkryh.katalyst.telemetry.model.DescriptorStatus
import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.tui.attach.RunDiscovery
import io.github.darkryh.katalyst.tui.attach.TelemetryClient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Attach lifecycle for the currently selected backend. */
sealed interface AttachStatus {
    /** Enumerating run descriptors; nothing selected yet. */
    data object Discovering : AttachStatus

    /** A live snapshot was just fetched from [descriptor]. */
    data class Attached(val descriptor: RunDescriptor) : AttachStatus

    /** No backend selectable, or the selected one stopped responding. */
    data object Detached : AttachStatus
}

/**
 * Immutable UI state observed by every screen through the top-level snapshot seam. Screens never see
 * this type directly — they receive [snapshot] (or one of its sections). The chrome reads the whole
 * thing.
 */
data class InspectorUiState(
    val attach: AttachStatus = AttachStatus.Discovering,
    val snapshot: TelemetrySnapshot? = null,
    val backends: List<RunDescriptor> = emptyList(),
    val selected: RunDescriptor? = null,
    val lastError: String? = null,
)

/**
 * Drives discovery + polling in its own [viewModelScope] and exposes an immutable [state] StateFlow.
 * Never throws on backend errors: discovery failures yield an empty backend list, and a dead backend
 * flips [AttachStatus.Detached] while retaining the last good snapshot for continuity.
 *
 * Held at app scope (obtained via `viewModel { InspectorViewModel() }` above `NavDisplay`), so it
 * survives navigation between tiles. The [TelemetryClient] is registered as a closeable and shut
 * down when the view model clears.
 */
class InspectorViewModel(
    /** Pin discovery to this backend pid (embedded mode passes its host process); null = free. */
    private val preferredPid: Long? = null,
) : StateViewModel<InspectorUiState>(InspectorUiState()) {

    private val client = TelemetryClient()

    init {
        addCloseable(client)
        launch { runLoop() }
    }

    /** Manually pin a backend (e.g. from a future picker); the loop keeps it selected while alive. */
    fun select(descriptor: RunDescriptor) {
        updateState { it.copy(selected = descriptor) }
    }

    /**
     * While true the loop idles without discovering, fetching, or publishing state — the UI sets
     * this when Dispatch hibernates, so an unwatched inspector allocates no snapshots and (with
     * nothing changing) paints no frames. Volatile: written by the UI, read by the poll coroutine.
     */
    @Volatile
    var paused: Boolean = false

    private suspend fun runLoop() {
        while (true) {
            if (paused) {
                delay(250.milliseconds)
                continue
            }
            // Drop descriptors a backend has already marked dead so a leftover file can't pin us.
            val found = runCatching { RunDiscovery.discover() }.getOrDefault(emptyList())
                .filter { it.status != DescriptorStatus.STOPPED && it.status != DescriptorStatus.STOPPING }

            // Try the pinned backend first (embedded = the host process, else the previous
            // selection), then any other live one — so a crashed or stale descriptor never traps
            // us: we fall through to a backend that actually answers.
            val pinnedPid = preferredPid ?: currentState.selected?.pid
            val ordered = buildList {
                pinnedPid?.let { pid -> found.firstOrNull { it.pid == pid }?.let(::add) }
                addAll(found.filter { it.pid != pinnedPid })
            }

            when (val live = attachToFirstLive(ordered)) {
                null -> updateState {
                    it.copy(
                        backends = found,
                        selected = ordered.firstOrNull(),
                        snapshot = it.snapshot, // retain last-good for continuity while detached
                        attach = AttachStatus.Detached,
                        lastError = ordered.firstOrNull()
                            ?.let { d -> "No response from ${d.appName} (pid ${d.pid})" },
                    )
                }

                else -> updateState {
                    it.copy(
                        backends = found,
                        selected = live.first,
                        snapshot = live.second,
                        attach = AttachStatus.Attached(live.first),
                        lastError = null,
                    )
                }
            }
            // Poll hot until an attached backend finishes booting — the embedded inspector starts
            // BEFORE the telemetry transport, so discovery itself must also be fast or the early
            // boot phases scroll past unseen. Settle to the dashboard cadence once boot completes.
            val settled = currentState.attach is AttachStatus.Attached &&
                currentState.snapshot?.health?.bootComplete == true
            delay((if (settled) 1_500 else 250).milliseconds)
        }
    }

    /** Probe candidates in order and attach to the first that returns a snapshot; null if none do. */
    private suspend fun attachToFirstLive(
        candidates: List<RunDescriptor>,
    ): Pair<RunDescriptor, TelemetrySnapshot>? {
        for (descriptor in candidates) {
            val snapshot = runCatching { client.fetchSnapshot(descriptor) }.getOrNull()
            if (snapshot != null) return descriptor to snapshot
        }
        return null
    }
}
