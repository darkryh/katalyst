package io.github.darkryh.katalyst.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import io.github.darkryh.katalyst.idea.psi.KatalystPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Flags functions that *look* like Katalyst route/middleware/websocket/exception-handler entrypoints
 * but won't actually be discovered, so the developer finds out in the editor instead of at runtime.
 *
 * Two cases:
 *  1. The function calls a `katalyst*` DSL but is `private`/`protected` — Katalyst only discovers
 *     public top-level functions, so it would be silently ignored.
 *  2. The function has a Ktor receiver and a route-ish name but calls no `katalyst*` DSL — almost
 *     certainly a forgotten DSL call (mirrors katalyst-analysis' INVALID_DSL_SIGNATURE check).
 */
class KatalystDslSignatureInspection : LocalInspectionTool() {

    private val routeNameHints = listOf(
        "routes", "route", "middleware", "websocket", "websockets", "exceptionhandler", "handlers",
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                val anchor = function.nameIdentifier ?: return
                val calls = KatalystPsi.dslCalls(function)

                if (calls.isNotEmpty()) {
                    val isHidden = function.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                        function.hasModifier(KtTokens.PROTECTED_KEYWORD)
                    if (isHidden) {
                        holder.registerProblem(
                            anchor,
                            "This function calls ${calls.joinToString()} but is not public, so Katalyst will not discover it. " +
                                "Make it a public top-level function.",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        )
                    }
                    return
                }

                // No DSL call but it is shaped/named like an entrypoint.
                if (function.receiverTypeReference != null &&
                    routeNameHints.any { function.name?.lowercase()?.endsWith(it) == true }
                ) {
                    holder.registerProblem(
                        anchor,
                        "This looks like a Katalyst route function but calls no katalyst* DSL, so it will not be discovered. " +
                            "Wrap the body in katalystRouting { } / katalystMiddleware { } / katalystWebSockets { } / " +
                            "katalystExceptionHandler { }.",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }
}
