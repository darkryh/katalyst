package io.github.darkryh.katalyst.repositories

/**
 * Marker interface for entities managed by repositories.
 */
interface Identifiable<Id : Comparable<Id>> {
    val id: Id?
}