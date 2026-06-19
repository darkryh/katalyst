# katalyst-intellij-plugin

Editor support for the Katalyst framework in **IntelliJ IDEA** and **Android Studio**.

Katalyst is annotation-free: it discovers entrypoints by interface, by routing-DSL bytecode, by
return type and by dual-binding (see [`katalyst-conventions`](../katalyst-conventions)). The IDE
can't see those reflective usages, so it reports framework code as *unused* and developers reach for
`@Suppress("unused")`. This plugin removes that friction.

## Features (MVP / 1.0)

- **No more `@Suppress("unused")`** — an `ImplicitUsageProvider` marks every Katalyst entrypoint as
  used: services, components, repositories, tables, event handlers, migrations, initializers, config
  loaders, scheduled-job methods, and the route/middleware/websocket/exception-handler DSL functions.
- **Gutter icons** next to each discovered entrypoint, with a tooltip naming its kind.
- **Signature inspection** — warns when a function looks like an entrypoint but won't be discovered
  (e.g. it's `private`, or it has a Ktor receiver and a route-ish name but forgot the `katalyst*`
  DSL call).

## Architecture: hybrid (PSI + metadata)

- **PSI** ([`psi/KatalystPsi.kt`](src/main/kotlin/io/github/darkryh/katalyst/idea/psi/KatalystPsi.kt))
  powers the instant features above. They need no build and update on every keystroke. Recognition
  uses the same closed rule-set as the runtime and `katalyst-analysis`, vendored in
  [`PluginConventions`](src/main/kotlin/io/github/darkryh/katalyst/idea/convention/PluginConventions.kt).
- **Metadata** ([`graph/KatalystGraphService.kt`](src/main/kotlin/io/github/darkryh/katalyst/idea/graph/KatalystGraphService.kt))
  reads `katalyst-graph.json` produced by `katalyst-analysis` for whole-app features (cross-node
  navigation, dependency diagnostics). It stays dormant until the file exists, and never replaces the
  live PSI rules — it only enriches them. Wiring these into editor features is the post-1.0 work.

This split keeps a single source of truth: the runtime is authoritative, `katalyst-analysis` mirrors
it from the classpath, and the plugin mirrors the same tiny rule-set in PSI while deferring deep
analysis to the shared `katalyst-graph.json`.

## Building

This is a **separate composite build** (it applies the IntelliJ Platform Gradle plugin and is not a
published library). It is included in the main build only when requested:

```bash
# from the repo root
./gradlew -PincludeIntellijPluginComposite=true :katalyst-intellij-plugin:buildPlugin
```

or standalone from this directory:

```bash
gradle buildPlugin   # or runIde to launch a sandbox IDE
```

Building downloads the IntelliJ Platform SDK (network required). The plugin targets `sinceBuild=242`
(2024.2) with no upper bound for 1.0.

## Publishing

Signing and JetBrains Marketplace publishing are wired up (all secrets via environment variables).
See [PUBLISHING.md](PUBLISHING.md) for the full checklist, the environment variables to set, and the
`gradle verifyPlugin` / `signPlugin` / `publishPlugin` flow.
