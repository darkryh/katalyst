# Repository Guidelines

## Project Structure & Module Organization
Katalyst is a multi-module Kotlin/Gradle **library** — there is no root application or
`src/` directory; the root `build.gradle.kts` only configures shared plugins and public-API
tracking. Each `katalyst-*` module keeps sources under `src/main/kotlin` and tests under
`src/test/kotlin`, mirroring its package (`io.github.darkryh.katalyst.<feature>`). See
`settings.gradle.kts` for the authoritative module list; the main groups are:

- **Core:** `katalyst-core`, `katalyst-di`, `katalyst-koin-bean`, `katalyst-scanner`
- **HTTP/engines:** `katalyst-ktor`, `katalyst-ktor-engine-{netty,jetty,cio}`, `katalyst-websockets`
- **Data:** `katalyst-persistence`, `katalyst-transactions`, `katalyst-migrations`
- **Configuration:** `katalyst-config-provider`, `katalyst-config-spi`, `katalyst-config-yaml`
- **Events:** `katalyst-events`, `katalyst-events-bus`
- **Observability:** `katalyst-telemetry-model`, `katalyst-telemetry`, `katalyst-tui`
- **Testing:** `katalyst-testing-core`, `katalyst-testing-ktor`
- **Starters** (dependency bundles, under `starter/`): `katalyst-starter-{core,web,persistence,
  migrations,scheduler,websockets,test,observability,engine-netty,engine-jetty,engine-cio}`
- **Packaging:** `katalyst-bom`, `katalyst-gradle-plugin`, `katalyst-conventions`

A runnable reference application lives in the separate `samples` composite build
(`samples/katalyst-example`). The IntelliJ plugin (`katalyst-intellij-plugin`) and the
Kotlin/Wasm project generator (`initializr`) are each their own standalone Gradle builds —
opt into the samples/plugin composites with `-PincludeSamplesComposite=true` /
`-PincludeIntellijPluginComposite=true`, or build the generator directly with
`./gradlew -p initializr wasmJsBrowserDistribution`.

## Build, Test, and Development Commands
- `./gradlew build` — compiles every module and runs checks (tests, public-API compatibility).
- `./gradlew test` — runs the full test suite across modules.
- `./gradlew :katalyst-scheduler:test` (or any `:module:test`) — targets a single module.
- `./gradlew apiDump` — re-snapshots a module's public API into `<module>/api/*.api` after an
  intentional signature change; CI's `apiCheck` fails the build on undeclared drift.
- `cd samples && ./gradlew :katalyst-example:koverHtmlReport` — coverage report for the
  reference app.

## Coding Style & Naming Conventions
`kotlin.code.style=official` (four-space indents, trailing commas where idiomatic). Keep
packages under `io.github.darkryh.katalyst.<feature>` and match directory layout to package
paths. Public APIs should carry KDoc summaries; complex flows can link back to the conceptual
docs under `docs/`. Prefer immutable data classes for configuration, and surface asynchronous
work with `suspend` functions.

## Testing Guidelines
Unit tests use `kotlin-test`/JUnit; Ktor-facing code uses `ktor-server-test-host` for pipeline
verification. Name unit-test files `*Test.kt`; a handful of multi-module or I/O suites use
`*IntegrationTests.kt`. Keep fixtures small and deterministic; prefer in-memory fakes over
embedded infrastructure. Run `./gradlew test` before publishing changes, and add cases
whenever you touch discovery, DI wiring, or transaction management.

## Commit & Pull Request Guidelines
Use imperative, scope-prefixed commit messages (e.g., `scheduler: tighten cron validation`).
Group related work into focused commits and avoid mixing refactors with feature delivery.
Pull requests should outline motivation, summarize module-level impacts, link to issues, and
include any configuration updates. Confirm tests pass locally and note follow-up tasks so
reviewers can verify quickly.

## Configuration & Security Notes
Keep secrets and environment-specific values outside the repo; YAML config should read
overrides from environment variables (`${VAR:default}`) or external files at deploy time. New
configuration keys belong in `docs/reference/configuration.md` and should bind through
`katalyst-config-provider` (`@ConfigPrefix`/`ConfigBinding`) so the scanner registers them
automatically instead of being read ad hoc.
