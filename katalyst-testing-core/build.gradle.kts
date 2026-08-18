plugins {
    id("io.github.darkryh.katalyst.conventions.testing")
}

dependencies {
    api(project(":katalyst-di"))
    api(project(":katalyst-core"))
    api(project(":katalyst-config-provider"))
    api(project(":katalyst-ktor"))
    api(project(":katalyst-persistence"))
    api(kotlin("test"))

    implementation(project(":katalyst-config-yaml"))
    implementation(project(":katalyst-events-bus"))
    implementation(project(":katalyst-migrations"))
    implementation(project(":katalyst-scheduler"))
    implementation(project(":katalyst-websockets"))
    implementation(project(":katalyst-events"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(kotlin("reflect"))
    // The in-memory engine reports index displacement through the framework logger, exactly as
    // KoinBeanEngine does; a fake that stays silent about an eviction is how #31 hid.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.h2)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    // Migration regression fixtures execute SQL through Exposed, and the boot tests assert
    // against the resulting schema.
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(project(":katalyst-migrations"))
    // On the compile classpath (not just runtime) so BeanEngineContractTest can assert the
    // test double and the production Koin engine agree on lookup semantics.
    testImplementation(project(":katalyst-koin-bean"))
    // Displacement reporting is part of the engine contract, so the level it is reported at is
    // asserted through a logback appender.
    testImplementation(libs.logback)

    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(libs.ktor.server.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    // The cross-engine bean contract lives in test fixtures so every module that owns a
    // KatalystBeanEngine implementation runs the same suite against it. It needs the JUnit 5
    // flavour of kotlin.test and the marker types the contract is stated over.
    testFixturesImplementation(libs.kotlin.test.junit5)
    testFixturesImplementation(project(":katalyst-events"))
}
