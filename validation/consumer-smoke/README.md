# consumer-smoke

A standalone consumer build that validates the **published** Katalyst consumption path. It is not
part of the main Katalyst build; it resolves the BOM, the starters and the
`io.github.darkryh.katalyst` Gradle plugin from `mavenLocal`.

What it proves:

- A real app builds applying **only** `id("io.github.darkryh.katalyst")` — no `kotlin`, no
  `io.ktor.plugin`, no serialization plugin.
- The build declares **no Ktor or Exposed** coordinate; both arrive transitively through the
  starters.
- The **engine is a swappable, single-coordinate choice**: `katalyst-starter-engine-<netty|jetty|cio>`.
  The two engines you didn't pick never reach the classpath.
- The full stack (DI, Exposed + H2, kotlinx.serialization, routing) boots and serves a request.

## Run

```bash
# 1. From the framework root, publish the artifacts under test:
./gradlew publishToMavenLocal

# 2. Validate every engine:
validation/consumer-smoke/run-all-engines.sh

# …or a single engine:
./gradlew -p validation/consumer-smoke check -PkatalystEngine=jetty

# …or boot it for real on H2 (Ctrl-C to stop):
./gradlew -p validation/consumer-smoke run -PkatalystEngine=netty
```
