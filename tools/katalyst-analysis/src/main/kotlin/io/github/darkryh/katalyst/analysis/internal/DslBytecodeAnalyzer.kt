package io.github.darkryh.katalyst.analysis.internal

import io.github.darkryh.katalyst.conventions.KatalystConventions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * Detects, via ASM bytecode analysis, which Katalyst routing DSL functions a method invokes.
 *
 * This is a faithful copy of the runtime's `AutoBindingRegistrar.scanForKatalystDslCalls`: a
 * top-level function with a Ktor receiver is only a genuine route module if its body actually
 * calls one of [KatalystConventions.dslMethodNames] declared by one of
 * [KatalystConventions.dslOwnerInternalNames]. Using the same bytecode check (and the same shared
 * constants) guarantees analysis and runtime classify the same functions as entrypoints.
 */
internal object DslBytecodeAnalyzer {
    private val logger = LoggerFactory.getLogger(DslBytecodeAnalyzer::class.java)

    /** Returns the set of Katalyst DSL functions invoked by [method]; empty if none. */
    fun dslCalls(method: Method, loader: ClassLoader): Set<String> {
        val resource = method.declaringClass.name.replace('.', '/') + ".class"
        val stream = method.declaringClass.classLoader?.getResourceAsStream(resource)
            ?: loader.getResourceAsStream(resource)
            ?: return emptySet()

        return stream.use { input ->
            val reader = ClassReader(input)
            val targetDescriptor = Type.getMethodDescriptor(method)
            val found = mutableSetOf<String>()

            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name != method.name || descriptor != targetDescriptor) return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            insnName: String,
                            insnDescriptor: String,
                            isInterface: Boolean,
                        ) {
                            if (owner in KatalystConventions.dslOwnerInternalNames &&
                                insnName in KatalystConventions.dslMethodNames
                            ) {
                                found += insnName
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

            found
        }.also {
            if (it.isNotEmpty()) logger.debug("{}.{} uses DSL {}", method.declaringClass.name, method.name, it)
        }
    }
}
