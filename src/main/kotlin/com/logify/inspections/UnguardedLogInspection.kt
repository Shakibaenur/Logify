package com.logify.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Inspection: warns on any Log.d/e/i/w/v call not guarded by if (BuildConfig.DEBUG).
 * Provides a quick-fix to wrap the call automatically.
 */
class UnguardedLogInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Unguarded Log call"
    override fun getGroupDisplayName(): String = "Logify"
    override fun getShortName(): String = "UnguardedLog"
    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return if (holder.file.name.endsWith(".kt")) kotlinVisitor(holder)
        else javaVisitor(holder)
    }

    // ── Java visitor ──────────────────────────────────────────────────────────

    private fun javaVisitor(holder: ProblemsHolder) = object : JavaElementVisitor() {
        override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
            try {
                val ref = expr.methodExpression
                val method = ref.referenceName ?: return
                val qualifier = ref.qualifierExpression?.text ?: return
                if (qualifier != "Log" || method !in LOG_METHODS) return
                if (!isGuarded(expr)) {
                    holder.registerProblem(
                        expr,
                        "Log call not guarded by BuildConfig.DEBUG — will appear in release builds",
                        ProblemHighlightType.WARNING,
                        WrapWithDebugGuardFix(isKotlin = false)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // ── Kotlin visitor ────────────────────────────────────────────────────────

    private fun kotlinVisitor(holder: ProblemsHolder) = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            try {
                val expr = element as? KtDotQualifiedExpression ?: return
                val receiver = expr.receiverExpression.text
                val call = expr.selectorExpression as? KtCallExpression ?: return
                val method = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return
                if (receiver != "Log" || method !in LOG_METHODS) return
                if (!isGuarded(expr)) {
                    holder.registerProblem(
                        expr,
                        "Log call not guarded by BuildConfig.DEBUG — will appear in release builds",
                        ProblemHighlightType.WARNING,
                        WrapWithDebugGuardFix(isKotlin = true)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // ── Guard detection ───────────────────────────────────────────────────────

    private fun isGuarded(element: PsiElement): Boolean {
        // Walk up the PSI tree looking for if (BuildConfig.DEBUG) { ... }
        var parent = element.parent
        while (parent != null) {
            when {
                parent is PsiIfStatement -> {
                    val condition = parent.condition?.text ?: ""
                    if (isDebugCondition(condition)) return true
                }
                parent is KtIfExpression -> {
                    val condition = parent.condition?.text ?: ""
                    if (isDebugCondition(condition)) return true
                }
            }
            parent = parent.parent
        }
        return false
    }

    private fun isDebugCondition(text: String): Boolean =
        text.contains("BuildConfig.DEBUG") || text.contains("BuildConfig.BUILD_TYPE")

    companion object {
        val LOG_METHODS = setOf("d", "e", "i", "w", "v")
    }
}

// ── Quick-fix ─────────────────────────────────────────────────────────────────

class WrapWithDebugGuardFix(private val isKotlin: Boolean) : LocalQuickFix {

    override fun getName(): String = "Wrap with BuildConfig.DEBUG check"
    override fun getFamilyName(): String = "Logify"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val element = descriptor.psiElement
            if (isKotlin) applyKotlinFix(project, element)
            else applyJavaFix(project, element)
        } catch (_: Exception) {}
    }

    private fun applyJavaFix(project: Project, element: PsiElement) {
        val factory = PsiElementFactory.getInstance(project)
        val logText = element.text
        val ifStatement = factory.createStatementFromText(
            "if (BuildConfig.DEBUG) { $logText; }", element
        )
        val statement = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiStatement::class.java) ?: return
        statement.replace(ifStatement)
    }

    private fun applyKotlinFix(project: Project, element: PsiElement) {
        val factory = KtPsiFactory(project)
        val logText = element.text
        val ifExpr = factory.createExpression("if (BuildConfig.DEBUG) { $logText }")
        val statement = PsiTreeUtil.getParentOfType(element, org.jetbrains.kotlin.psi.KtExpression::class.java,
            false) ?: return
        statement.replace(ifExpr)
    }
}
