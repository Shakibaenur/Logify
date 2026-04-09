package com.logify.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.logify.utils.PsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Intention action: shows "Normalize log tag" in the context menu when the caret
 * is inside a Log.d/e/i/w/v call whose first argument is a "bad" tag string such
 * as "TAG", "DEBUG", "test", etc.  Replaces it with "ClassName#methodName".
 */
class NormalizeLogTagIntention : PsiElementBaseIntentionAction() {

    override fun getText(): String = "Normalize log tag"
    override fun getFamilyName(): String = "Logify"

    // ── Availability ─────────────────────────────────────────────────────────

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return try {
            findLogCallWithBadTag(element) != null
        } catch (e: Exception) {
            false
        }
    }

    // ── Invocation (already inside a write action) ────────────────────────────

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        try {
            val logCall = findLogCallWithBadTag(element) ?: return
            val newTag = PsiUtils.getLogContext(element).toTag()
            when (logCall) {
                is PsiMethodCallExpression -> replaceJavaTag(project, logCall, newTag)
                is KtDotQualifiedExpression -> replaceKotlinTag(project, logCall, newTag)
            }
        } catch (e: Exception) {
            // Never crash the IDE
        }
    }

    // ── PSI detection ─────────────────────────────────────────────────────────

    private fun findLogCallWithBadTag(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 15) {
            when {
                current is PsiMethodCallExpression && isJavaBadLogCall(current) -> return current
                current is KtDotQualifiedExpression && isKotlinBadLogCall(current) -> return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun isJavaBadLogCall(expr: PsiMethodCallExpression): Boolean {
        val ref    = expr.methodExpression
        val method = ref.referenceName ?: return false
        val qual   = ref.qualifierExpression?.text ?: return false
        if (qual != "Log" || method !in LOG_METHODS) return false

        val args = expr.argumentList.expressions
        if (args.isEmpty()) return false
        return isBadTagExpr(args[0])
    }

    private fun isBadTagExpr(expr: PsiElement): Boolean = when (expr) {
        is PsiLiteralExpression -> (expr.value as? String)?.let { it in BAD_TAGS } ?: false
        is PsiReferenceExpression -> expr.referenceName?.let { it in BAD_TAGS } ?: false
        else -> false
    }

    private fun isKotlinBadLogCall(expr: KtDotQualifiedExpression): Boolean {
        val receiver = expr.receiverExpression.text
        val call     = expr.selectorExpression as? KtCallExpression ?: return false
        val method   = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return false
        if (receiver != "Log" || method !in LOG_METHODS) return false

        val args = call.valueArguments
        if (args.isEmpty()) return false
        return when (val argExpr = args[0].getArgumentExpression()) {
            is KtStringTemplateExpression -> argExpr.text.trim('"') in BAD_TAGS
            is KtNameReferenceExpression  -> argExpr.text in BAD_TAGS
            else -> false
        }
    }

    // ── Replacement ───────────────────────────────────────────────────────────

    private fun replaceJavaTag(project: Project, call: PsiMethodCallExpression, newTag: String) {
        val factory = PsiElementFactory.getInstance(project)
        val newLit  = factory.createExpressionFromText("\"$newTag\"", call)
        call.argumentList.expressions.firstOrNull()?.replace(newLit)
    }

    private fun replaceKotlinTag(project: Project, call: KtDotQualifiedExpression, newTag: String) {
        val factory = KtPsiFactory(project)
        val newExpr = factory.createExpression("\"$newTag\"")
        val selector = call.selectorExpression as? KtCallExpression ?: return
        selector.valueArguments.firstOrNull()?.getArgumentExpression()?.replace(newExpr)
    }

    companion object {
        val LOG_METHODS = setOf("d", "e", "i", "w", "v")
        val BAD_TAGS = setOf(
            "TAG", "tag", "DEBUG", "debug", "INFO", "info",
            "WARN", "warn", "ERROR", "error", "LOG", "Log", "log",
            "test", "Test", "TEST", "MyApp", "App"
        )
    }
}
