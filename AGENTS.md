# Repository Guidelines

## Project Structure & Module Organization
Katalyst is a multi-module Kotlin/Gradle project. The root `build.gradle.kts` wires modules such as `katalyst-core`, `katalyst-persistence`, `katalyst-scheduler`, `katalyst-ktor-support`, `scanner`, and `dependency-injection`. Each module keeps Kotlin sources under `src/main/kotlin` and tests in `src/test/kotlin`; shared assets sit in matching `resources` folders. The executable Ktor host lives in `src/main/kotlin` at the root, with runtime configuration in `src/main/resources/application.yaml`. A ready-to-run reference app is under `io.github.darkryh.katalyst.example`, broken into `infra`, `repositories`, `services`, `routes`, and `scheduler` packages so you can see how the modules plug together. Refer to `KATALYST_LOGIC_CODE.md` when you need deeper architectural context.

## Build, Test, and Development Commands
- `./gradlew build` — compiles all modules, runs checks, and assembles the Ktor server.
- `./gradlew test` — executes the full test suite across modules.
- `./gradlew :katalyst-scheduler:test` (or any `:module:test`) — targets a single module while iterating.
- `./gradlew run` — boots the local Ktor server using the active `application.yaml`.

## Coding Style & Naming Conventions
We follow `kotlin.code.style=official` (four-space indents, trailing commas where idiomatic). Keep packages under `io.github.darkryh.katalyst.<feature>` and match directory layout to package paths. Public APIs should carry KDoc summaries; complex flows can link back to the conceptual docs in `docs/` or the top-level guides. Prefer immutable data classes for configuration (see `katalyst-persistence/DatabaseConfig.kt`) and surface asynchronous work with `suspend` functions.

## Testing Guidelines
Unit tests rely on `kotlin-test` with JUnit; Ktor components use `ktor-server-test-host` for pipeline verification. Name files with `*Test.kt` for unit coverage and `*IntegrationTest.kt` for multi-module or I/O flows, mirroring the scheduler suite. Keep fixtures small and deterministic; mock external services via in-memory doubles before reaching for embedded infrastructure. Run `./gradlew test` before publishing changes and add new cases whenever you touch core discovery, DI wiring, or transaction management.

## Commit & Pull Request Guidelines
Use imperative, scope-prefixed commit messages (e.g., `scheduler: tighten cron validation`). Group related work into focused commits and avoid mixing refactors with feature delivery. Pull requests should outline motivation, summarize module-level impacts, link to issues, and include any configuration updates or new endpoints. Attach screenshots or logs if the change alters runtime behavior. Confirm tests pass locally and note any follow-up tasks so reviewers can verify quickly.

## Configuration & Security Notes
Keep secrets and environment-specific values outside the repo; `application.yaml` should load overrides from environment variables or external files when deployed. When adding new configuration blocks, document defaults inline and thread them through the `database` and `dependency-injection` modules so the scanner can detect them automatically.
