package io.github.darkryh.katalyst.initializr

import io.github.darkryh.katalyst.initializr.model.ConfigField
import io.github.darkryh.katalyst.initializr.model.Engine
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.FormState
import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.model.ProjectConfigValidator
import io.github.darkryh.katalyst.initializr.model.deriveArtifactId
import io.github.darkryh.katalyst.initializr.model.deriveGroupId
import io.github.darkryh.katalyst.initializr.model.toProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelTest {
    // ---- feature selection & the migrations→persistence constraint ----

    @Test
    fun migrationsAreDisabledWithoutPersistence() {
        val orphan = FeatureSelection(features = setOf(Feature.MIGRATIONS))
        assertFalse(orphan.isEnabled(Feature.MIGRATIONS), "migrations must not enable without persistence")
        assertFalse(Feature.MIGRATIONS in orphan.enabled)

        val ok = FeatureSelection(features = setOf(Feature.PERSISTENCE, Feature.MIGRATIONS))
        assertTrue(ok.isEnabled(Feature.MIGRATIONS))
    }

    @Test
    fun defaultSelectionIsEverythingOn() {
        assertEquals(Feature.entries.toSet(), FeatureSelection.DEFAULT.enabled.toSet())
        assertEquals(Engine.NETTY, FeatureSelection.DEFAULT.engine)
    }

    @Test
    fun presetsResolveAsExpected() {
        assertTrue(FeatureSelection.MINIMAL.enabled.isEmpty())
        assertEquals(
            setOf(Feature.PERSISTENCE, Feature.MIGRATIONS, Feature.OBSERVABILITY),
            FeatureSelection.STANDARD.enabled.toSet(),
        )
        assertEquals(Feature.entries.toSet(), FeatureSelection.FULL.enabled.toSet())
    }

    @Test
    fun togglingIsReversible() {
        val base = FeatureSelection(features = emptySet())
        val on = base.toggled(Feature.SCHEDULER)
        assertTrue(on.isEnabled(Feature.SCHEDULER))
        assertFalse(on.toggled(Feature.SCHEDULER).isEnabled(Feature.SCHEDULER))
    }

    // ---- derivation ----

    @Test
    fun derivesGroupAndArtifact() {
        assertEquals("com.example", deriveGroupId("com.example.myapp"))
        assertEquals("my-katalyst-app", deriveArtifactId("My Katalyst App", "com.example.app"))
        // fallbacks
        assertEquals("app", deriveArtifactId("", "com.example.app").let { if (it.isEmpty()) "app" else it })
        assertEquals("payments", deriveArtifactId("", "com.acme.payments"))
    }

    // ---- validation ----

    @Test
    fun rejectsReservedKeywordPackageSegment() {
        val bad = ProjectConfig.DEFAULT.copy(packageName = "com.class.app")
        val fields = ProjectConfigValidator.validate(bad).map { it.field }
        assertTrue(ConfigField.PACKAGE_NAME in fields, "reserved keyword segment must fail")
    }

    @Test
    fun rejectsBadPackageArtifactAndGroup() {
        val bad =
            ProjectConfig.DEFAULT.copy(
                packageName = "com.1bad.pkg",
                artifactId = "Bad_Artifact",
                groupId = "Com.Acme",
            )
        val fields = ProjectConfigValidator.validate(bad).map { it.field }.toSet()
        assertTrue(fields.containsAll(setOf(ConfigField.PACKAGE_NAME, ConfigField.ARTIFACT_ID, ConfigField.GROUP_ID)))
    }

    @Test
    fun acceptsCommonVersionForms() {
        for (v in listOf("1", "1.0", "1.0.0", "0.1.0-SNAPSHOT", "1.2.3-rc1")) {
            assertTrue(ProjectConfigValidator.isValid(ProjectConfig.DEFAULT.copy(appVersion = v)), "should accept $v")
        }
        assertFalse(ProjectConfigValidator.isValid(ProjectConfig.DEFAULT.copy(appVersion = "1..0")))
    }

    @Test
    fun formHidesDerivedCoordinateErrors() {
        // A form with only the three simple fields, no overrides: derived group/artifact can't fail,
        // so no GROUP_ID/ARTIFACT_ID errors are ever surfaced.
        val form = FormState.DEFAULT
        val fields = ProjectConfigValidator.validate(form).map { it.field }
        assertFalse(ConfigField.GROUP_ID in fields)
        assertFalse(ConfigField.ARTIFACT_ID in fields)
        assertTrue(ProjectConfigValidator.isValid(form))
    }

    @Test
    fun formOverrideSurfacesCoordinateError() {
        val form = FormState.DEFAULT.copy(groupIdOverride = "Bad.Group")
        val fields = ProjectConfigValidator.validate(form).map { it.field }
        assertTrue(ConfigField.GROUP_ID in fields, "an invalid override must surface")
    }

    @Test
    fun formToProjectConfigCarriesSelection() {
        val form = FormState.DEFAULT.copy(selection = FeatureSelection.STANDARD)
        assertEquals(FeatureSelection.STANDARD, form.toProjectConfig().selection)
    }
}
