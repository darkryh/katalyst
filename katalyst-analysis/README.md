# katalyst-analysis

The **semantic analysis layer** for Katalyst applications. It produces a static, serialisable
[`KatalystApplicationGraph`](src/main/kotlin/io/github/darkryh/katalyst/analysis/model/Model.kt):
a description of how a Katalyst app is assembled — components, services, repositories, tables,
routes, middleware, websockets, exception handlers, schedulers, event handlers, initializers,
migrations, config loaders, the DI dependency edges between them, and diagnostics.

It is plain JVM code: **not** a Gradle plugin, and it does **not** depend on the IntelliJ Platform.
The same module is reusable from tests, CLIs, Gradle tasks and the IntelliJ plugin.

## Why it exists

Katalyst is annotation-free. Entrypoints are recognised by *interface* (`Service`, `EventHandler`,
…), by *bytecode* (a top-level `Route`/`Application` function that calls `katalystRouting` /
`katalystMiddleware` / `katalystWebSockets` / `katalystExceptionHandler`), by *return type* (a
service method returning `SchedulerJobHandle`), and by *dual-binding* (an Exposed `Table` that also
implements the Katalyst `Table` marker). The runtime knows all this — but only while booting. This
module mirrors those exact rules **without booting the app** so tooling can reason about them.

## Single source of truth

- Discovery **names** come from [`katalyst-conventions`](../katalyst-conventions) — the same
  constants the runtime uses, so analysis and runtime can't disagree about what an entrypoint is.
- The **DI dependency graph and validation** are produced by *reusing* `katalyst-di`'s own
  `DependencyAnalyzer` and `DependencyValidator`, driven with a no-op container
  ([`StaticAnalysisContainer`](src/main/kotlin/io/github/darkryh/katalyst/analysis/internal/StaticAnalysisContainer.kt))
  so nothing is instantiated. The diagnostics you get are the ones the application would boot (or
  fail to boot) with.
- Classes are loaded with `Class.forName(name, initialize = false)` — static initialisers never run.

## Usage

```kotlin
val graph = KatalystAnalyzer().analyze(
    KatalystAnalysisConfig(
        scanPackages = listOf("com.example.app"),
        classpath = runtimeClasspathFiles, // application output + dependencies
    )
)

graph.routes.forEach { println("route ${it.symbol.simpleName} -> ${it.dslCalls}") }
graph.diagnostics
    .filter { it.severity == DiagnosticSeverity.ERROR }
    .forEach { println("${it.kind}: ${it.message}") }

// Opt-in serialisation consumed by the IntelliJ plugin / CI.
KatalystGraphJson.writeTo(File("build/katalyst/katalyst-graph.json"), graph)
```

## Three views, one model

The in-memory `KatalystApplicationGraph` is primary. `katalyst-graph.json`
([`KatalystGraphJson`](src/main/kotlin/io/github/darkryh/katalyst/analysis/export/KatalystGraphJson.kt))
is a second serialisation of the *same* model — never a separate truth — for tools that prefer to
read a file rather than re-run analysis.
