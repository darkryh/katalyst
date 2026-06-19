package io.github.darkryh.katalyst.analysis.internal

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Small java-generic-reflection helpers used to read marker type arguments
 * (e.g. the `E` in `EventHandler<E>`, the `Entity` in `CrudRepository<Id, Entity>`).
 *
 * We use java generics rather than kotlin-reflect because the analysed classes come from a
 * foreign [java.net.URLClassLoader]; java's `genericInterfaces` traversal is robust across
 * classloaders and never triggers class initialisation.
 */
internal object GenericTypes {

    /**
     * Finds the [argIndex]-th type argument that [clazz] supplies to the generic interface
     * named [interfaceFqName], searching the whole supertype hierarchy. Returns the raw
     * argument class, or null if it cannot be resolved statically (e.g. it is itself a type
     * variable).
     */
    fun argumentOf(clazz: Class<*>, interfaceFqName: String, argIndex: Int): Class<*>? {
        val visited = mutableSetOf<Type>()
        val queue = ArrayDeque<Type>()
        queue += clazz.genericInterfaces
        clazz.genericSuperclass?.let { queue += it }

        while (queue.isNotEmpty()) {
            val type = queue.removeFirst()
            if (!visited.add(type)) continue

            when (type) {
                is ParameterizedType -> {
                    val raw = type.rawType
                    if (raw is Class<*>) {
                        if (raw.name == interfaceFqName) {
                            return type.actualTypeArguments.getOrNull(argIndex) as? Class<*>
                        }
                        queue += raw.genericInterfaces
                        raw.genericSuperclass?.let { queue += it }
                    }
                }
                is Class<*> -> {
                    queue += type.genericInterfaces
                    type.genericSuperclass?.let { queue += it }
                }
            }
        }
        return null
    }
}
