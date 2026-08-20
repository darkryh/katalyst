package io.github.darkryh.katalyst.idea

import io.github.darkryh.katalyst.idea.convention.PluginConventions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * Drift guard between the plugin's vendored [PluginConventions] and the canonical
 * `io.github.darkryh.katalyst.conventions.KatalystConventions` (from the published
 * katalyst-conventions module, a test-only dependency).
 *
 * The plugin deliberately vendors the discovery contract so it can stay a self-contained composite
 * build — but that copy silently drifted across the framework's pre-release renames. This test makes
 * any future drift a build failure: bump the `katalyst-conventions` version in build.gradle.kts and,
 * if a constant moved, this fails until the vendored copy is re-synced.
 *
 * It loads the canonical object via **reflection from an isolated classloader** on purpose: the
 * plugin compiles with Kotlin 2.0, which cannot read katalyst-conventions' newer Kotlin metadata at
 * compile time, and the jar's `.kotlin_module` would even break compilation if it were on the
 * compile classpath. So the build resolves the jar on a separate configuration and passes its path
 * via the `katalyst.conventions.jar` system property; here we load it reflectively (the JVM ignores
 * Kotlin metadata) and still compare against the real published artifact.
 */
class PluginConventionsParityTest {

    private val conventions: Class<*> = run {
        val jar = System.getProperty("katalyst.conventions.jar")
            ?: error("katalyst.conventions.jar system property not set by the build")
        val loader = URLClassLoader(arrayOf(File(jar).toURI().toURL()), javaClass.classLoader)
        Class.forName("io.github.darkryh.katalyst.conventions.KatalystConventions", true, loader)
    }
    private val instance: Any = conventions.getField("INSTANCE").get(null)

    private fun const(name: String): String = conventions.getField(name).get(null) as String

    @Suppress("UNCHECKED_CAST")
    private fun collection(getter: String): Set<String> =
        (conventions.getMethod(getter).invoke(instance) as Collection<String>).toSet()

    @Test
    fun `marker interfaces match the canonical conventions`() {
        assertEquals(collection("getMarkerInterfaces"), PluginConventions.markerInterfaces.toSet())
    }

    @Test
    fun `marker annotations match the canonical conventions`() {
        assertEquals(collection("getMarkerAnnotations"), PluginConventions.markerAnnotations)
    }

    @Test
    fun `dsl method names match the canonical conventions`() {
        assertEquals(collection("getDslMethodNames"), PluginConventions.dslMethodNames)
    }

    @Test
    fun `dsl owner qualified names match the canonical conventions`() {
        assertEquals(collection("getDslOwnerQualifiedNames"), PluginConventions.dslOwnerQualifiedNames)
    }

    @Test
    fun `individual marker and DSL FQNs match the canonical conventions`() {
        assertEquals(const("SERVICE"), PluginConventions.SERVICE)
        assertEquals(const("COMPONENT"), PluginConventions.COMPONENT)
        assertEquals(const("CRUD_REPOSITORY"), PluginConventions.CRUD_REPOSITORY)
        assertEquals(const("TABLE"), PluginConventions.TABLE)
        assertEquals(const("EVENT_HANDLER"), PluginConventions.EVENT_HANDLER)
        assertEquals(const("KTOR_MODULE"), PluginConventions.KTOR_MODULE)
        assertEquals(const("KATALYST_MIGRATION"), PluginConventions.KATALYST_MIGRATION)
        assertEquals(const("APPLICATION_INITIALIZER"), PluginConventions.APPLICATION_INITIALIZER)
        assertEquals(const("APPLICATION_READY_INITIALIZER"), PluginConventions.APPLICATION_READY_INITIALIZER)
        assertEquals(const("CONFIG_BINDING"), PluginConventions.CONFIG_BINDING)
        assertEquals(const("CONFIG_PREFIX"), PluginConventions.CONFIG_PREFIX)
        assertEquals(const("SCHEDULER_JOB_HANDLE"), PluginConventions.SCHEDULER_JOB_HANDLE)
        assertEquals(const("DSL_ROUTING"), PluginConventions.DSL_ROUTING)
        assertEquals(const("DSL_WEBSOCKETS"), PluginConventions.DSL_WEBSOCKETS)
        assertEquals(const("DSL_EXCEPTION_HANDLER"), PluginConventions.DSL_EXCEPTION_HANDLER)
        assertEquals(const("DSL_MIDDLEWARE"), PluginConventions.DSL_MIDDLEWARE)
    }
}
