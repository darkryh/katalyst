plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystStarterPersistence)
    api(projects.katalystMigrations)
}
