plugins {
    `java-platform`
    `maven-publish`
}

group = providers.gradleProperty("katalystGroup").get()
version = providers.gradleProperty("katalystVersion").get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it.name.startsWith("katalyst-") && it.name != project.name }
            .forEach { api(project(it.path)) }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            artifactId = project.name
        }
    }
}
