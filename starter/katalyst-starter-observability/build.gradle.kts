plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

// One-line opt-in: the bounded in-process telemetry layer + the embedded Dispatch TUI inspector.
// In a real terminal it becomes the default console (boot splash then live dashboard); without a
// TTY (services, IDE run windows) it falls back to plain logs with a one-time how-to notice. Opt
// out at runtime with -Dkatalyst.telemetry.enabled=false / -Dkatalyst.tui.enabled=false (or the
// KATALYST_TUI_ENABLED=false env var).
//
// Kept OUT of katalyst-starter-core deliberately: katalyst-telemetry's capturers reach into every
// subsystem they observe (persistence, transactions, migrations, scheduler, events, ktor), so
// bundling it into core would force those modules onto every app's runtime classpath regardless
// of which starters it actually chose — exactly the kind of forced bundling the engine-decoupled
// starters (katalyst-starter-engine-*) were built to avoid.
dependencies {
    api(projects.katalystTelemetry)
    api(projects.katalystTui)
}
