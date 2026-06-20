plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystCore)
    api(projects.katalystDi)
    api(projects.katalystKoinBean)
    api(projects.katalystScanner)
    api(projects.katalystConfigProvider)
    api(projects.katalystConfigYaml)
    api(projects.katalystEvents)
    api(projects.katalystEventsBus)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.slf4j.api)

    runtimeOnly(libs.logback)
}
