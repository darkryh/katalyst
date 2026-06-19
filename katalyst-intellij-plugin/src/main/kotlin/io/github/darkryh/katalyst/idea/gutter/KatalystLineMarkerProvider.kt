package io.github.darkryh.katalyst.idea.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import io.github.darkryh.katalyst.idea.psi.EntrypointKind
import io.github.darkryh.katalyst.idea.psi.KatalystPsi
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Renders a gutter icon next to every Katalyst entrypoint declaration, with a tooltip naming the
 * kind (service, route, repository, …). This gives the framework's reflectively-wired code the same
 * at-a-glance visibility annotation-based frameworks get for free, and reassures developers that a
 * symbol with no call sites is genuinely wired.
 *
 * The marker is anchored on the declaration's name identifier (a leaf element), as the platform
 * requires, to avoid "line marker on non-leaf element" warnings.
 */
class KatalystLineMarkerProvider : LineMarkerProvider {

    // Entrypoint recognition resolves (light classes, inheritance, light methods), so it must not
    // run in the fast getLineMarkerInfo pass — modern IDEs forbid resolve there. We do nothing fast
    // and emit markers in the slow (background) pass instead.
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        for (element in elements) {
            val kind = entrypointKindForNameLeaf(element) ?: continue
            val tooltip = kind.label
            result.add(
                LineMarkerInfo(
                    element,
                    element.textRange,
                    AllIcons.Nodes.Plugin,
                    { tooltip },
                    null,
                    GutterIconRenderer.Alignment.LEFT,
                    { tooltip },
                )
            )
        }
    }

    private fun entrypointKindForNameLeaf(element: PsiElement): EntrypointKind? {
        val parent = element.parent ?: return null
        return when (parent) {
            is KtClassOrObject -> if (parent.nameIdentifier === element) KatalystPsi.classKind(parent) else null
            is KtNamedFunction -> if (parent.nameIdentifier === element) KatalystPsi.functionKind(parent) else null
            else -> null
        }
    }
}
