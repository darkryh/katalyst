plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.ead"
version = "0.0.1"

application { mainClass = "io.ktor.server.netty.EngineMain" }

dependencies {
    // Katalyst modules
    implementation("com.ead.katalyst:katalyst-core")
    implementation("com.ead.katalyst:katalyst-transactions")
    implementation("com.ead.katalyst:katalyst-persistence")
    implementation("com.ead.katalyst:katalyst-ktor")
    implementation("com.ead.katalyst:katalyst-scanner")
    implementation("com.ead.katalyst:katalyst-di")
    implementation("com.ead.katalyst:katalyst-migrations")
    implementation("com.ead.katalyst:katalyst-scheduler")
    implementation("com.ead.katalyst:katalyst-websockets")
    implementation("com.ead.katalyst:katalyst-ktor-engine-netty")

    // Configuration management
    implementation("com.ead.katalyst:katalyst-config-provider")
    implementation("com.ead.katalyst:katalyst-config-yaml")

    // Event system
    implementation("com.ead.katalyst:katalyst-events")
    implementation("com.ead.katalyst:katalyst-events-bus")
    implementation("com.ead.katalyst:katalyst-events-transport")
    implementation("com.ead.katalyst:katalyst-events-client")

    // Messaging abstraction
    /*implementation("com.ead.katalyst:katalyst-messaging")
    implementation("com.ead.katalyst:katalyst-messaging-amqp"))*/

    // testing katalyst
    testImplementation("com.ead.katalyst:katalyst-testing-core")
    testImplementation("com.ead.katalyst:katalyst-testing-ktor")

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