import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    // Publish through vanniktech (same as every library module) so the BOM is actually uploaded to
    // Maven Central, signed. The previous plain `maven-publish` setup only ever reached mavenLocal —
    // alpha01 shipped without a BOM on Central, breaking any consumer that used platform(...).
    id("com.vanniktech.maven.publish")
}

group = providers.gradleProperty("katalystGroup").get()
version = providers.gradleProperty("katalystVersion").get()

javaPlatform {
    allowDependencies()
}

// Modules that are NOT consumer library dependencies and therefore must never be a BOM constraint:
//  - katalyst-bom: the platform itself.
//  - katalyst-gradle-plugin: a Gradle plugin, resolved via pluginManagement (a marker artifact),
//    not via `implementation` — constraining it would be meaningless and misleading.
// (memory-validation is excluded by the `katalyst-` name filter and is not published anyway.)
val nonLibraryModules = setOf(
    project.name,
    "katalyst-gradle-plugin",
)

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it.name.startsWith("katalyst-") && it.name !in nonLibraryModules }
            .forEach { api(project(it.path)) }
    }
}

mavenPublishing {
    // java-platform has no main artifact; vanniktech needs to be told to publish the platform.
    configure(JavaPlatform())

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = name,
        version = version.toString(),
    )

    pom {
        name.set("Katalyst - $name")
        description.set("Katalyst bill of materials: aligned versions for all katalyst-* modules")
        url.set("https://github.com/darkryh/katalyst")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/darkryh/katalyst")
            connection.set("scm:git:git://github.com/darkryh/katalyst.git")
            developerConnection.set("scm:git:ssh://github.com/darkryh/katalyst.git")
        }

        developers {
            developer {
                id.set("Darkryh")
                name.set("Xavier Alexander Torres Calderón")
                email.set("alex_torres-xc@hotmail.com")
            }
        }
    }
}
