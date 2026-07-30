package io.github.darkryh.katalyst.di.internal

/**
 * Returns the elements of this list without repeats, comparing by object identity.
 *
 * Katalyst discovers extension-point instances through two channels at once: the framework
 * registries and the bean container. A scanned instance legitimately appears in both, so call
 * sites that union the two must collapse the overlap or the instance is used twice — a hook
 * executed twice, an event handler subscribed twice, a route installed twice.
 *
 * Identity is the correct comparison, not class: two *distinct* instances that happen to share
 * a class are both legitimate and must both survive.
 */
internal fun <T> List<T>.distinctByIdentity(): List<T> {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())
    return filter { seen.add(it) }
}
