plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

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
    implementation("com.ead.katalyst:katalyst-config-provider")
    implementation("com.ead.katalyst:katalyst-config-yaml")
    implementation("com.ead.katalyst:katalyst-ktor-engine")
    implementation("com.ead.katalyst:katalyst-ktor-engine-netty")

    // Feature modules
    implementation(project(":boshi-server:boshi-shared"))
    implementation(project(":boshi-server:boshi-smtp"))
    implementation(project(":boshi-server:boshi-auth"))
    implementation(project(":boshi-server:boshi-storage"))
    implementation(project(":boshi-server:boshi-retention"))

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(libs.logback)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation("com.ead.katalyst:katalyst-testing-core")
    testImplementation("com.ead.katalyst:katalyst-testing-ktor")
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}

application {
    mainClass.set("com.ead.boshi.app.ApplicationKt")
}

// Create a fat JAR with all dependencies for standalone execution
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.ead.boshi.app.ApplicationKt"
        )
    }

    // Include all runtime dependencies in the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.RSA")
        exclude("META-INF/maven/**")
        exclude("META-INF/proguard/**")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
