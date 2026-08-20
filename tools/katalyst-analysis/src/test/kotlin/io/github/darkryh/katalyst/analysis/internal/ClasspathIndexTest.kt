package io.github.darkryh.katalyst.analysis.internal

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Guards against [ClasspathIndex] leaking its isolated `URLClassLoader` (and the jar file handle
 * behind it) on every `analyze()` call.
 *
 * A real on-disk jar is used (rather than a plain directory classpath entry) because closing a
 * directory-backed `URLClassLoader` is effectively a no-op -- there is no persistent file handle to
 * release. A jar entry does hold one, which is exactly the resource [ClasspathIndex.close] must free.
 */
class ClasspathIndexTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `close releases the jar so classes not yet loaded can no longer be resolved`() {
        val packageName = "katalyst.classpathindextest.fixture"
        val jar = buildFixtureJar(packageName, "FixtureA", "FixtureB")

        val index = ClasspathIndex.build(listOf(jar), listOf(packageName))

        // Sanity: resolvable while the loader is open.
        assertNotNull(index.loadOrNull("$packageName.FixtureA"), "FixtureA should load before close()")

        index.close()
        index.close() // must be idempotent -- a second close() must not throw

        // FixtureB was never loaded before close(), so resolving it now requires reading the jar
        // afresh. If the loader (and its jar file handle) were genuinely released, this must fail.
        assertNull(
            index.loadOrNull("$packageName.FixtureB"),
            "classes not yet loaded must not resolve once the isolated loader is closed",
        )
    }

    private fun buildFixtureJar(packageName: String, vararg classNames: String): File {
        val workDir = Files.createTempDirectory("classpath-index-test").toFile().also { tempDirs += it }
        val srcDir = File(workDir, "src/${packageName.replace('.', '/')}").apply { mkdirs() }
        val outDir = File(workDir, "out").apply { mkdirs() }

        val sourceFiles = classNames.map { name ->
            File(srcDir, "$name.java").apply {
                writeText("package $packageName;\npublic class $name {}\n")
            }
        }

        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("No system Java compiler available; this test must run on a JDK, not a JRE.")
        val result = compiler.run(
            null, null, null,
            "-d", outDir.path,
            *sourceFiles.map { it.path }.toTypedArray(),
        )
        check(result == 0) { "Failed to compile fixture classes for ClasspathIndexTest" }

        val jarFile = File(workDir, "fixture.jar")
        JarOutputStream(jarFile.outputStream()).use { jar ->
            outDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
                jar.putNextEntry(JarEntry(classFile.relativeTo(outDir).invariantSeparatorsPath))
                classFile.inputStream().use { it.copyTo(jar) }
                jar.closeEntry()
            }
        }
        return jarFile
    }
}
