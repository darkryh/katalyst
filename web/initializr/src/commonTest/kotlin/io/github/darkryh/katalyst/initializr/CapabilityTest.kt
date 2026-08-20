package io.github.darkryh.katalyst.initializr

import io.github.darkryh.katalyst.initializr.model.Capability
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.isCapabilityOn
import io.github.darkryh.katalyst.initializr.model.toggleCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityTest {
    @Test
    fun defaultSelectionHasEveryCapabilityOn() {
        val sel = FeatureSelection.DEFAULT
        Capability.entries.forEach { assertTrue(sel.isCapabilityOn(it), "${it.title} should be on by default") }
    }

    @Test
    fun togglingDatabaseOffRemovesPersistenceAndMigrations() {
        val off = FeatureSelection.DEFAULT.toggleCapability(Capability.DATABASE)
        assertFalse(off.isCapabilityOn(Capability.DATABASE))
        assertFalse(Feature.PERSISTENCE in off.features)
        assertFalse(Feature.MIGRATIONS in off.features)
        // Other capabilities are untouched.
        assertTrue(off.isCapabilityOn(Capability.SCHEDULED_JOBS))
        assertTrue(off.isCapabilityOn(Capability.MONITORING))
    }

    @Test
    fun togglingDatabaseBackOnRestoresBothStarters() {
        val toggled = FeatureSelection.DEFAULT
            .toggleCapability(Capability.DATABASE)
            .toggleCapability(Capability.DATABASE)
        assertTrue(Feature.PERSISTENCE in toggled.features)
        assertTrue(Feature.MIGRATIONS in toggled.features)
    }

    @Test
    fun singleFeatureCapabilitiesMapToTheirStarter() {
        assertEquals(setOf(Feature.SCHEDULER), Capability.SCHEDULED_JOBS.bundled)
        assertEquals(setOf(Feature.WEBSOCKETS), Capability.REALTIME.bundled)
        assertEquals(setOf(Feature.OBSERVABILITY), Capability.MONITORING.bundled)

        val minimal = FeatureSelection(features = emptySet())
        assertFalse(minimal.isCapabilityOn(Capability.REALTIME))
        val withRealtime = minimal.toggleCapability(Capability.REALTIME)
        assertTrue(withRealtime.isCapabilityOn(Capability.REALTIME))
        assertEquals(setOf(Feature.WEBSOCKETS), withRealtime.features)
    }
}
