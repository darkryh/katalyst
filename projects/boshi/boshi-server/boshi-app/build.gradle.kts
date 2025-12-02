plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)

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

// Configure ShadowJar for fat JAR creation
tasks.shadowJar {
    archiveFileName.set("boshi-server.jar")

    // Merge service files (important for Ktor and other frameworks)
    mergeServiceFiles()

    // Exclude unnecessary files to reduce JAR size
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
    exclude("**/module-info.class")

    // Manifest configuration
    manifest {
        attributes(
            "Main-Class" to "com.ead.boshi.app.ApplicationKt",
            "Implementation-Title" to "Boshi SMTP Server",
            "Implementation-Version" to project.version,
            "Created-By" to "Gradle ShadowJar"
        )
    }

    // Use shadowJar as the default JAR
    isZip64 = true
}

// Make build task depend on shadowJar
tasks.build {
    dependsOn(tasks.shadowJar)
}
