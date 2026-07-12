package io.github.darkryh.katalyst.idea.scheduler

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * PSI helpers for the Katalyst scheduler DSL (`scheduler.jobs { cron(...) ; fixedDelay(...) ; ... }`).
 *
 * Everything here is resolve-light: it matches the DSL by callee name and lexical nesting, never by
 * symbol resolution, so it works on a snippet without katalyst-scheduler on the classpath.
 */
internal object SchedulerPsi {

    /** The job-builder functions inside a `jobs { }` block. */
    val builderNames: Set<String> = setOf("cron", "fixedDelay", "fixedRate", "oneTime")
    private val cronExpressionArgumentNames = setOf("expression", "cronExpression")
    private val nonCronExpressionArgumentNames = setOf("taskName", "config")

    /** True if [call] is lexically inside a `jobs { }` builder lambda. */
    fun isInsideJobsBlock(call: KtCallExpression): Boolean =
        generateSequence(call.parent) { it.parent }
            .filterIsInstance<KtCallExpression>()
            .any { it.calleeExpression?.text == "jobs" }

    /**
     * The string-literal cron expression argument of a `cron(...)` call, covering:
     * - `cron("name", "expr")`
     * - `cron(taskName = "name", expression = "expr")`
     * - `cron(config = ScheduleConfig(...), expression = "expr")`
     * - `cron(..., CronExpression("expr"))`
     *
     * Returns null for non-static expressions and interpolated strings.
     */
    fun cronExpressionLiteral(call: KtCallExpression): KtStringTemplateExpression? {
        val named = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() in cronExpressionArgumentNames
        }
        named?.getArgumentExpression()?.stringLiteralFromCronExpression()?.let { return it }

        val positional = call.valueArguments.filter { it.getArgumentName() == null }
        positional.getOrNull(1)?.getArgumentExpression()?.stringLiteralFromCronExpression()?.let { return it }

        return call.valueArguments
            .asSequence()
            .filterNot { it.getArgumentName()?.asName?.asString() in nonCronExpressionArgumentNames }
            .mapNotNull { it.getArgumentExpression()?.stringLiteralFromCronExpression() }
            .firstOrNull()
    }

    /**
     * The static job-name literal of a builder call, either directly on the builder or inside a
     * `ScheduleConfig(...)` argument. Returns null when the name is computed.
     */
    fun nameLiteral(call: KtCallExpression): KtStringTemplateExpression? {
        val namedTaskName = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() == "taskName"
        }?.getArgumentExpression() as? KtStringTemplateExpression
        if (namedTaskName != null) return namedTaskName

        val firstPositional = call.valueArguments.firstOrNull { it.getArgumentName() == null }
            ?.getArgumentExpression()
        val directName = firstPositional as? KtStringTemplateExpression
        if (directName != null) return directName

        val configExpression = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() == "config"
        }?.getArgumentExpression() ?: firstPositional

        return configExpression?.scheduleConfigTaskNameLiteral()
    }

    /** The constant text of a string template, or null if it contains interpolation. */
    fun constantText(literal: KtStringTemplateExpression): String? {
        val entries = literal.entries
        if (entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return entries.joinToString("") { it.text }
    }

    private fun KtExpression.stringLiteralFromCronExpression(): KtStringTemplateExpression? {
        (this as? KtStringTemplateExpression)?.let { return it }

        val call = this as? KtCallExpression ?: return null
        if (call.calleeExpression?.text != "CronExpression") return null

        val argument = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() in cronExpressionArgumentNames
        } ?: call.valueArguments.firstOrNull { it.getArgumentName() == null }

        return argument?.getArgumentExpression() as? KtStringTemplateExpression
    }

    private fun KtExpression.scheduleConfigTaskNameLiteral(): KtStringTemplateExpression? {
        val call = this as? KtCallExpression ?: return null
        if (call.calleeExpression?.text != "ScheduleConfig") return null

        val argument = call.valueArguments.firstOrNull {
            it.getArgumentName()?.asName?.asString() == "taskName"
        } ?: call.valueArguments.firstOrNull { it.getArgumentName() == null }

        return argument?.getArgumentExpression() as? KtStringTemplateExpression
    }
}
