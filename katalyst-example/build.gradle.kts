plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.ead"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    // Katalyst modules
    implementation(project(":katalyst-core"))
    implementation(project(":katalyst-transactions"))
    implementation(project(":katalyst-persistence"))
    implementation(project(":katalyst-ktor"))
    implementation(project(":katalyst-scanner"))
    implementation(project(":katalyst-di"))
    implementation(project(":katalyst-migrations"))
    implementation(project(":katalyst-scheduler"))
    implementation(project(":katalyst-websockets"))

    // Ktor engine implementation
    implementation(project(":katalyst-ktor-engine"))
    implementation(project(":katalyst-ktor-engine-netty"))

    // Event system
    implementation(project(":katalyst-events"))
    implementation(project(":katalyst-events-bus"))
    implementation(project(":katalyst-events-transport"))
    implementation(project(":katalyst-events-client"))

    // Messaging abstraction
    implementation(project(":katalyst-messaging"))

    implementation(project(":katalyst-messaging-amqp"))

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

    // Dependency injection & messaging
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.rabbitmq.server)

    // Logging
    implementation(libs.logback)

    // YAML configuration
    implementation(libs.snakeyaml)

    // Testing
testImplementation(libs.ktor.server.test.host)
testImplementation(kotlin("test"))
testImplementation(libs.testcontainers.postgresql)

tasks.test {
    useJUnitPlatform()
}
}
