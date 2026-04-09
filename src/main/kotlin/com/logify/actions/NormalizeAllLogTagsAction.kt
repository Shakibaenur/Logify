package com.logify.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.logify.intentions.NormalizeLogTagIntention.Companion.LOG_METHODS
import com.logify.utils.PsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Right-click action: rewrites EVERY Log.d/e/i/w/v call in the current file so
 * each tag becomes "ClassName#methodName".  Works for both Java and Kotlin files.
 */
class NormalizeAllLogTagsAction : AnAction("Normalize All Log Tags in File") {

    override fun update(e: AnActionEvent) {
        val name = e.getData(CommonDataKeys.PSI_FILE)?.name ?: ""
        e.presentation.isEnabledAndVisible = name.endsWith(".kt") || name.endsWith(".java")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file    = e.getData(CommonDataKeys.PSI_FILE) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Normalize All Log Tags", null, {
            try {
                if (file.name.endsWith(".kt")) normalizeKotlin(project, file)
                else if (file.name.endsWith(".java")) normalizeJava(project, file)
            } catch (ex: Exception) {
                // Never crash the IDE
            }
        })
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun normalizeJava(project: com.intellij.openapi.project.Project, file: PsiFile) {
        val factory = PsiElementFactory.getInstance(project)
        // Collect first to avoid ConcurrentModificationException
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
            .filter { isJavaLogCall(it) }

        for (call in calls) {
            val args = call.argumentList.expressions
            if (args.isEmpty() || args[0] !is PsiLiteralExpression) continue
            val newTag = PsiUtils.getLogContext(call).toTag()
            val newLit = factory.createExpressionFromText("\"$newTag\"", call)
            args[0].replace(newLit)
        }
    }

    private fun isJavaLogCall(expr: PsiMethodCallExpression): Boolean {
        val ref = expr.methodExpression
        return ref.qualifierExpression?.text == "Log" && ref.referenceName in LOG_METHODS
    }

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun normalizeKotlin(project: com.intellij.openapi.project.Project, file: PsiFile) {
        val factory = KtPsiFactory(project)
        val calls = PsiTreeUtil.findChildrenOfType(file, KtDotQualifiedExpression::class.java)
            .filter { isKotlinLogCall(it) }

        for (call in calls) {
            val selector = call.selectorExpression as? KtCallExpression ?: continue
            val args     = selector.valueArguments
            if (args.isEmpty()) continue
            val argExpr  = args[0].getArgumentExpression() as? KtStringTemplateExpression ?: continue
            val newTag   = PsiUtils.getLogContext(call).toTag()
            val newExpr  = factory.createExpression("\"$newTag\"")
            argExpr.replace(newExpr)
        }
    }

    private fun isKotlinLogCall(expr: KtDotQualifiedExpression): Boolean {
        val receiver = expr.receiverExpression.text
        val call     = expr.selectorExpression as? KtCallExpression ?: return false
        val method   = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return false
        return receiver == "Log" && method in LOG_METHODS
    }
}
