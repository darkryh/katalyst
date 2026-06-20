plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "io.github.darkryh"
version = "0.0.1"

application { mainClass = "io.github.darkryh.katalyst.example.ApplicationKt" }

dependencies {
    val katalyst = "1.0.0-alpha01"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")
    testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

// IntelliJ/Tooling may request this task as a project-scoped path.
// Keep a local alias so :katalyst-example:prepareKotlinBuildScriptModel resolves.
tasks.register("prepareKotlinBuildScriptModel") {
    dependsOn(":prepareKotlinBuildScriptModel")
}
