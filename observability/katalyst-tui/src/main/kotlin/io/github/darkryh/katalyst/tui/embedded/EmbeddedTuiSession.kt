package io.github.darkryh.katalyst.tui.embedded

/**
 * What the inspector knows about how it was launched, and what it wants to happen when it quits.
 *
 * Both flags are read from a different thread than the one that writes them — the command palette
 * runs on the Dispatch UI dispatcher, [EmbeddedTuiFeature] on the backend's boot thread — hence
 * `@Volatile` on each.
 */
object EmbeddedTuiSession {

    /**
     * Cross-thread handshake between the command palette and [EmbeddedTuiFeature]: `/exit` sets this
     * before requesting a clean Dispatch shutdown, so the feature knows to keep the backend alive
     * (restore console logging, let the daemon TUI thread end) instead of stopping the whole process,
     * which is what quitting the inspector means otherwise (`/shutdown`, Ctrl+C).
     */
    @Volatile
    var detachRequested: Boolean = false

    /**
     * Whether this inspector is running INSIDE the backend it is showing.
     *
     * `/shutdown` means two structurally different things depending on the answer, which is why the
     * command was quietly wrong for so long:
     *
     *  - **Embedded** (the TUI is the application's console) — the inspector and the backend are one
     *    process, so quitting the inspector stops the backend. Nothing else to do.
     *  - **Standalone** (`./tui.sh`) — they are two processes connected over a loopback socket.
     *    Quitting the inspector stops *the inspector*. Actually stopping the backend means asking
     *    it to, and confirming that it did.
     *
     * Set once by [EmbeddedTuiFeature] before the UI starts; false for a standalone inspector, which
     * never runs that code at all.
     */
    @Volatile
    var embedded: Boolean = false
}
