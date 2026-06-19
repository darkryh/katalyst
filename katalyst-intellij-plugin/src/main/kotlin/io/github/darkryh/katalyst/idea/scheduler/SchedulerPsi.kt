package io.github.darkryh.katalyst.idea.scheduler

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * PSI helpers for the Katalyst scheduler DSL (`scheduler.jobs { cron(...) ; fixedDelay(...) ; ... }`).
 *
 * Everything here is resolve-light: it matches the DSL by callee name and lexical nesting, never by
 * symbol resolution, so it works on a snippet without katalyst-scheduler on the classpath.
 */
object SchedulerPsi {

    /** The job-builder functions inside a `jobs { }` block. */
    val builderNames: Set<String> = setOf("cron", "fixedDelay", "fixedRate", "oneTime")

    /** True if [call] is lexically inside a `jobs { }` builder lambda. */
    fun isInsideJobsBlock(call: KtCallExpression): Boolean =
        generateSequence(call.parent) { it.parent }
            .filterIsInstance<KtCallExpression>()
            .any { it.calleeExpression?.text == "jobs" }

    /**
     * The string-literal cron expression argument of a `cron(...)` call, covering both the positional
     * form `cron(name, "expr")` and the named `cron(config = …, cronExpression = "expr")`.
     * Returns null for a non-literal (e.g. a `CronExpression(...)` or an interpolated string).
     */
    fun cronExpressionLiteral(call: KtCallExpression): KtStringTemplateExpression? {
        val named = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() == "cronExpression"
        }
        val arg = named ?: call.valueArguments.filter { it.getArgumentName() == null }.getOrNull(1)
        return arg?.getArgumentExpression() as? KtStringTemplateExpression
    }

    /** The first positional string-literal argument of a builder call — the job name in the name form. */
    fun nameLiteral(call: KtCallExpression): KtStringTemplateExpression? =
        call.valueArguments.firstOrNull { it.getArgumentName() == null }
            ?.getArgumentExpression() as? KtStringTemplateExpression

    /** The constant text of a string template, or null if it contains interpolation. */
    fun constantText(literal: KtStringTemplateExpression): String? {
        val entries = literal.entries
        if (entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return entries.joinToString("") { it.text }
    }
}
