# Katalyst Documentation Index

This guide replaces the older long-form write‑ups and highlights the material that matches the current codebase.

## Library Snapshot
- **katalyst-core** – shared contracts (`Component`, `Service`, `Validator`, `DomainEvent`), transaction manager.
- **katalyst-persistence** – Exposed/Hikari integration and repository helpers.
- **katalyst-di** – bootstrap DSL, auto-binding registrar, scheduler wiring.
- **katalyst-ktor** – route/exception DSLs plus Koin-aware helper extensions.
- **katalyst-scanner** – reflection utilities for component discovery (see refreshed tests under `src/test`).
- **katalyst-scheduler** – coroutine-backed task scheduler with cron/fixed-delay helpers.

The reference application under `src/main` remains unchanged and is still the recommended usage example.

## Working With The Repo
- Build everything: `./gradlew build`
- Run focused module tests (new/updated suites):
  - `./gradlew :katalyst-di:test`
  - `./gradlew :katalyst-ktor:test`
  - `./gradlew :katalyst-scheduler:test`
  - `./gradlew :katalyst-scanner:test`

## Where To Read More
- **KATALYST_COMPREHENSIVE_OVERVIEW.md** – still useful for module walkthroughs, but treat DI/scanner sections as historical context.
- **KATALYST_ARCHITECTURE_GUIDE.md** – diagrams and lifecycle flows; cross-check against the refactored registrar/scheduler behaviour.
- Module-level source files now contain slimmer KDoc notes reflecting the current APIs.

Older experimental documents in `unused/` remain archived for reference only.
