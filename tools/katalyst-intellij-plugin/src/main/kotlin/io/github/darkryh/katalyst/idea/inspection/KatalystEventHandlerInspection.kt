package io.github.darkryh.katalyst.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Flags a Katalyst `EventHandler<T>` whose `override val eventType` does not match its type argument.
 *
 * `EventHandler<UserCreatedEvent>` with `override val eventType = UserDeletedEvent::class` compiles
 * cleanly, but the bus routes by `eventType`, so the handler is wired to the wrong event and never
 * fires for the type it declares — surfacing only at runtime (`WrongEventTypeException` / silence).
 *
 * The check is intentionally conservative and resolve-light: it compares simple type names and only
 * reports when both the type argument and the `X::class` receiver are plain name references that
 * clearly differ. (A mismatch that relies on two distinct types sharing a simple name is not flagged
 * — vanishingly rare, and not worth a false positive.)
 */
class KatalystEventHandlerInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                val handledType = eventHandlerTypeArgument(klass) ?: return
                val (declaredType, anchor) = declaredEventType(klass) ?: return
                if (handledType != declaredType) {
                    holder.registerProblem(
                        anchor,
                        "eventType is $declaredType::class but this EventHandler is declared for $handledType. " +
                            "The event bus routes by eventType, so it must match the type argument " +
                            "(EventHandler<$handledType>).",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }

    /** The simple name of `T` in `EventHandler<T>` from the supertype list, or null. */
    private fun eventHandlerTypeArgument(klass: KtClass): String? {
        val entry = klass.superTypeListEntries.firstOrNull {
            (it.typeReference?.typeElement as? KtUserType)?.referencedName == "EventHandler"
        } ?: return null
        val userType = entry.typeReference?.typeElement as? KtUserType ?: return null
        val argType = userType.typeArgumentList?.arguments?.firstOrNull()
            ?.typeReference?.typeElement as? KtUserType ?: return null
        return argType.referencedName
    }

    /** The simple name in `override val eventType = X::class`, paired with the anchor to mark. */
    private fun declaredEventType(klass: KtClass): Pair<String, KtClassLiteralExpression>? {
        val property = klass.declarations.filterIsInstance<KtProperty>()
            .firstOrNull { it.name == "eventType" } ?: return null
        val classLiteral = property.initializer as? KtClassLiteralExpression ?: return null
        val receiver = classLiteral.receiverExpression?.text?.substringAfterLast('.') ?: return null
        return receiver to classLiteral
    }
}
