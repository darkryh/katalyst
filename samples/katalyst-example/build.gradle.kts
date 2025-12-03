plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "io.github.darkryh"
version = "0.0.1"

application { mainClass = "io.github.darkryh.katalyst.example.ApplicationKt" }

dependencies {
    // Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core")
    implementation("io.github.darkryh.katalyst:katalyst-transactions")
    implementation("io.github.darkryh.katalyst:katalyst-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-ktor")
    implementation("io.github.darkryh.katalyst:katalyst-scanner")
    implementation("io.github.darkryh.katalyst:katalyst-di")
    implementation("io.github.darkryh.katalyst:katalyst-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-websockets")
    implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-netty")

    // Configuration management
    implementation("io.github.darkryh.katalyst:katalyst-config-provider")
    implementation("io.github.darkryh.katalyst:katalyst-config-yaml")

    // Event system
    implementation("io.github.darkryh.katalyst:katalyst-events")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus")
    implementation("io.github.darkryh.katalyst:katalyst-events-transport")
    implementation("io.github.darkryh.katalyst:katalyst-events-client")

    // Messaging abstraction
    /*implementation("io.github.darkryh.katalyst:katalyst-messaging")
    implementation("io.github.darkryh.katalyst:katalyst-messaging-amqp"))*/

    // testing katalyst
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-core")
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-ktor")

    // Ktor server surface
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.web.sockets)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)

    // Persistence
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.postgresql)

    // client experimental and unusable
    //implementation(libs.rabbitmq.server)

    // Logging
    implementation(libs.logback)

    // YAML configuration
    implementation(libs.snakeyaml)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}

tasks.test {
    useJUnitPlatform()
}
