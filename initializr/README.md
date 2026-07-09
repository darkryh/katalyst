# Katalyst Initializr

A browser-based project generator for **Katalyst**, written entirely in Kotlin (Kotlin/Wasm + Compose
Multiplatform) and served as static files from the same GitHub Pages site as the docs
(`/new/`). Pick a name, package and version, choose an engine and toggle the feature starters →
download a ready-to-run Katalyst service, zipped **entirely in the browser**. There is no backend.

## What it generates

The generated project scales with the selection — only the chosen starters appear in `build.gradle.kts`,
the `Application.kt` bootstrap, and `application.yaml`, and each feature contributes a tiny working
demonstrator:

| Selection | Adds to the download |
|---|---|
| **engine** (netty / jetty / cio) | the engine starter + `engine(…Server)` |
| **persistence** | `starter-persistence`, `database { fromConfiguration() }`, a `Note` table/entity/repository/route |
| **migrations** *(needs persistence)* | `starter-migrations`, `enableMigrations()`, `V1CreateNotes` |
| **scheduler** | `starter-scheduler`, `enableScheduler()`, a `HeartbeatTask` |
| **websockets** | `starter-websockets`, `enableWebSockets()`, a `NotificationSocketRoutes` |
| **observability** | `starter-observability` **+ an executable `run.sh`** + a TUI-aware `logback.xml` |

`run.sh` is gated on observability on purpose: it installs a native launcher with `installDist` and
execs it into a real TTY, which is what makes the embedded Katalyst TUI inspector render — the whole
point of the observability starter.

## Architecture

```
initializr/                         standalone Gradle build (NOT in the root settings.gradle.kts)
  src/commonMain/kotlin/.../
    model/        ProjectConfig, FeatureSelection, Derivation, ProjectConfigValidator   pure, JVM-tested
    template/     StarterTemplate (feature-aware, placeholdered)                         the app, as data
    generate/     ProjectGenerator ({{TOKEN}} + {{D}}->$ substitution)
    zip/          Crc32, ZipArchive (STORED, 0755 for run.sh), Base64                    pure-Kotlin zipping
  src/commonTest/kotlin/            GenerationTest, ModelTest                            run on the JVM
  src/wasmJsMain/kotlin/.../
    Main.kt                         ComposeViewport entry
    ui/Theme.kt                     the indigo palette (light + dark), twin of the docs' Material scheme
    ui/WiringGraph.kt               the live Compose-Canvas dependency graph
    ui/ConfigurePanels.kt           identity + validation strip + advanced, engine, presets, feature cards
    ui/Assembly.kt                  the graph + tabs + live code/tree preview
    ui/InitializrApp.kt             top bar, two-pane console, action bar, generate→zip→download
    platform/Download.kt            the ONLY browser interop (data-URL anchor download)
```

**Design principles**

- **Template as data.** The starter app is a typed, placeholdered structure assembled from the user's
  `FeatureSelection` — type-checked, deterministic, and unit-tested (every placeholder substituted, the
  DSL and dependency list match the selection, `migrations` requires `persistence`, `run.sh` is
  executable).
- **Pure core, thin shell.** Generation, substitution, zipping and Base64 are all `commonMain` and
  tested on the JVM. The browser layer is a single anchor-click in `Download.kt`.
- **Katalyst's own identity.** An indigo *assembly console* (not a terminal): a live **wiring graph**
  showing exactly what Katalyst discovers and injects at boot, with **fail-fast validation** surfaced
  as you type — the same discipline the framework applies at startup.
- **Standalone build.** Not in the root `settings.gradle.kts`, so a plain `./gradlew build` of the
  library never resolves the Compose/Wasm toolchain.

## Build & run locally

```bash
# From the repository root:
./gradlew -p initializr jvmTest                     # fast unit tests of the generator
./gradlew -p initializr wasmJsBrowserDistribution   # build the static site
./gradlew -p initializr wasmJsBrowserDevelopmentRun # serve with hot reload at http://localhost:8080
```

The production distribution lands in `initializr/build/dist/wasmJs/productionExecutable/`.

## Deploy

Deployment is handled by [`.github/workflows/docs.yml`](../.github/workflows/docs.yml): it builds the
MkDocs site **and** this Wasm distribution and publishes them together to GitHub Pages — docs at the
root, the initializr under `/new/`. The generated projects are pinned to the released Katalyst version
(`-PkatalystVersion`, derived from the release tag), so cutting any release republishes the generator
pinned to that version automatically.

## Notes & trade-offs

- **The generated project depends on published artifacts** (`io.github.darkryh.katalyst:*`), so it
  compiles once the matching release is on Maven Central.
- **Bundle size.** Compose Multiplatform renders via Skia, so the site ships an ~8 MB `skiko.wasm`
  (cached after first load).
- **No Gradle wrapper jar** is shipped in the generated project (binary blobs don't belong in a text
  template); its README / `run.sh` instruct a one-time `gradle wrapper --gradle-version <v>`.
