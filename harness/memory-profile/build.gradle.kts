plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.darkryh.katalyst.memory.MemoryValidationRunnerKt"
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(kotlin("test"))
}

// Resolved from the repository root, not by directory depth: this module has moved once already
// (root -> harness/) and a relative "../samples" silently breaks on the next move.
val samplesDir = rootProject.layout.projectDirectory.dir("samples")
val sampleInstallDir = samplesDir.dir("katalyst-example/build/install/katalyst-example")
val prepareSample by tasks.registering(Exec::class) {
    workingDir(samplesDir)
    commandLine("./gradlew", "--no-daemon", "--console=plain", ":katalyst-example:installDist")
}

tasks.named<JavaExec>("run") {
    dependsOn(prepareSample)
    args(
        "--mode=${providers.gradleProperty("memory.mode").getOrElse("baseline")}",
        "--sample-dir=${sampleInstallDir.asFile.absolutePath}",
    )
}

tasks.register<JavaExec>("memoryBaseline") {
    group = "verification"
    description = "Runs the full-backend memory validation workload"
    dependsOn(prepareSample)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "--mode=${providers.gradleProperty("memory.mode").getOrElse("baseline")}",
        "--sample-dir=${sampleInstallDir.asFile.absolutePath}",
    )
}
