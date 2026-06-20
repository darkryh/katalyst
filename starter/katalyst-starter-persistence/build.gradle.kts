plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystStarterCore)
    api(projects.katalystPersistence)
    api(projects.katalystTransactions)
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.hikari)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
}
