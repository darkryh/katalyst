package io.github.darkryh.katalyst.tui.embedded

/**
 * Cross-thread handshake between the command palette and [EmbeddedTuiFeature]: `/exit` sets
 * [detachRequested] before requesting a clean Dispatch shutdown, so the feature knows to keep the
 * backend alive (restore console logging, let the daemon TUI thread end) instead of stopping the
 * whole process, which is what quitting the inspector means otherwise (`/shutdown`, Ctrl+C).
 */
object EmbeddedTuiSession {
    @Volatile
    var detachRequested: Boolean = false
}
