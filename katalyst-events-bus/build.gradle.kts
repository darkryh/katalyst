plugins {
    id("io.github.darkryh.katalyst.conventions.common")
    // Real JMH microbenchmarks (Phase 4). Benchmarks live in `src/jmh/kotlin`; run with
    // `./gradlew :katalyst-events-bus:jmh`. This supersedes the interim MicroBench harness.
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    // Core events module
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)

    // Transaction module for event deferral
    implementation(projects.katalystTransactions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(kotlin("reflect"))
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlinx.coroutines.test)
    // Logback's ListAppender is how the tests assert that the framework *reports* a problem
    // (dropped registrations, dropped observation events) instead of failing silently.
    testImplementation(libs.logback)
}

jmh {
    // Keep the default invocation quick; tune up for serious measurement runs.
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    timeUnit.set("us")
}
