package io.github.darkryh.katalyst.idea.inlay

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.darkryh.katalyst.idea.scheduler.KatalystCron
import io.github.darkryh.katalyst.idea.scheduler.SchedulerPsi
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Renders a Katalyst cron expression in plain English as an inline hint right after the literal —
 * e.g. `cron("nightly", "0 0 2 * * ?")  Every day at 02:00`.
 *
 * This is the readability half of the scheduler tooling (the inspection is the validity half). It is
 * the same pattern IntelliJ's Spring support uses for `@Scheduled(cron = …)`. Resolve-light: it keys
 * off the `cron(...)` callee inside a `jobs { }` block and the literal text only.
 */
class KatalystCronInlayProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
        object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (element !is KtCallExpression) return
                if (element.calleeExpression?.text != "cron") return
                if (!SchedulerPsi.isInsideJobsBlock(element)) return

                val literal = SchedulerPsi.cronExpressionLiteral(element) ?: return
                val text = SchedulerPsi.constantText(literal) ?: return
                val description = KatalystCron.describe(text) ?: return

                sink.addPresentation(
                    InlineInlayPosition(literal.textRange.endOffset, relatedToPrevious = true),
                    hintFormat = HintFormat.default,
                ) {
                    text(description)
                }
            }
        }
}
