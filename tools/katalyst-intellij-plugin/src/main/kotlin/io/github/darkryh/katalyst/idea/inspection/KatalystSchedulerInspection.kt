package io.github.darkryh.katalyst.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import io.github.darkryh.katalyst.idea.scheduler.KatalystCron
import io.github.darkryh.katalyst.idea.scheduler.SchedulerPsi
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Editor-time validation for the Katalyst scheduler DSL, so mistakes that would otherwise only
 * surface at startup (as `SchedulerValidationException`) are caught while typing.
 *
 * Two checks, both resolve-light and scoped to a `jobs { }` block:
 *  1. An invalid cron literal in `cron(name, "expr")` / `cron(config, cronExpression = "expr")` —
 *     underlined with the same field-precise message the runtime validator produces.
 *  2. Two jobs in the same `jobs { }` block declared with the same name (name form) — a real conflict.
 */
class KatalystSchedulerInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                when (expression.calleeExpression?.text) {
                    "cron" -> if (SchedulerPsi.isInsideJobsBlock(expression)) checkCron(expression, holder)
                    "jobs" -> checkDuplicateNames(expression, holder)
                }
            }
        }

    private fun checkCron(call: KtCallExpression, holder: ProblemsHolder) {
        val literal = SchedulerPsi.cronExpressionLiteral(call) ?: return
        val text = SchedulerPsi.constantText(literal) ?: return // interpolated — can't validate statically
        val errors = KatalystCron.validate(text)
        if (errors.isNotEmpty()) {
            holder.registerProblem(
                literal,
                "Invalid cron expression: ${errors.joinToString("; ")}",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }

    private fun checkDuplicateNames(jobsCall: KtCallExpression, holder: ProblemsHolder) {
        val body = jobsCall.lambdaArguments.firstOrNull()
            ?.getLambdaExpression()?.bodyExpression as? KtBlockExpression ?: return

        val byName = LinkedHashMap<String, MutableList<org.jetbrains.kotlin.psi.KtStringTemplateExpression>>()
        body.statements.filterIsInstance<KtCallExpression>()
            .filter { it.calleeExpression?.text in SchedulerPsi.builderNames }
            .forEach { builder ->
                val nameLiteral = SchedulerPsi.nameLiteral(builder) ?: return@forEach
                val name = SchedulerPsi.constantText(nameLiteral) ?: return@forEach
                byName.getOrPut(name) { mutableListOf() }.add(nameLiteral)
            }

        byName.values.filter { it.size > 1 }.forEach { duplicates ->
            duplicates.forEach { literal ->
                holder.registerProblem(
                    literal,
                    "Duplicate scheduler job name — each job in a jobs { } block must have a unique name.",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }
}
