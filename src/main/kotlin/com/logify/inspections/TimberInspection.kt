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
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Inspection: warns on any Log.d/e/i/w/v call and suggests using Timber instead.
 * Disabled by default — enable in Settings > Editor > Inspections > Logify when your project uses Timber.
 */
class TimberInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Log call should use Timber"
    override fun getGroupDisplayName(): String = "Logify"
    override fun getShortName(): String = "UseTimberInsteadOfLog"
    override fun isEnabledByDefault(): Boolean = false

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (holder.file.name.endsWith(".kt")) kotlinVisitor(holder)
        else javaVisitor(holder)

    // ── Java visitor ──────────────────────────────────────────────────────────

    private fun javaVisitor(holder: ProblemsHolder) = object : JavaElementVisitor() {
        override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
            try {
                val ref = expr.methodExpression
                val method = ref.referenceName ?: return
                val qualifier = ref.qualifierExpression?.text ?: return
                if (qualifier != "Log" || method !in LOG_METHODS) return
                holder.registerProblem(
                    expr,
                    "Prefer Timber over android.util.Log",
                    ProblemHighlightType.WARNING,
                    ConvertToTimberFix(isKotlin = false)
                )
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
                holder.registerProblem(
                    expr,
                    "Prefer Timber over android.util.Log",
                    ProblemHighlightType.WARNING,
                    ConvertToTimberFix(isKotlin = true)
                )
            } catch (_: Exception) {}
        }
    }

    companion object {
        val LOG_METHODS = setOf("d", "e", "i", "w", "v")
    }
}

// ── Quick-fix ─────────────────────────────────────────────────────────────────

class ConvertToTimberFix(private val isKotlin: Boolean) : LocalQuickFix {

    override fun getName(): String = "Convert to Timber"
    override fun getFamilyName(): String = "Logify"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val element = descriptor.psiElement
            if (isKotlin) applyKotlinFix(project, element)
            else applyJavaFix(project, element)
        } catch (_: Exception) {}
    }

    private fun applyKotlinFix(project: Project, element: PsiElement) {
        val expr = element as? KtDotQualifiedExpression ?: return
        val newExpr = buildTimberKotlinExpr(project, expr) ?: return
        expr.replace(newExpr)
    }

    private fun applyJavaFix(project: Project, element: PsiElement) {
        val call = element as? PsiMethodCallExpression ?: return
        val newExpr = buildTimberJavaExpr(project, call) ?: return
        call.replace(newExpr)
    }
}

// ── Timber expression builders (shared with ConvertLogToTimberAction) ─────────

fun buildTimberKotlinExpr(project: Project, expr: KtDotQualifiedExpression): KtExpression? {
    val call = expr.selectorExpression as? KtCallExpression ?: return null
    val methodName = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return null
    val args: List<KtValueArgument> = call.valueArguments
    if (args.size < 2) return null
    if (args.any { it.isNamed() }) return null  // skip named-arg calls to avoid incorrect reordering

    val msgText = args[1].getArgumentExpression()?.text ?: return null
    val throwableText = args.getOrNull(2)?.getArgumentExpression()?.text

    val newText = if (methodName == "e" && throwableText != null)
        "Timber.e($throwableText, $msgText)"
    else
        "Timber.$methodName($msgText)"

    return KtPsiFactory(project).createExpression(newText)
}

fun buildTimberJavaExpr(project: Project, call: PsiMethodCallExpression): PsiExpression? {
    val methodName = call.methodExpression.referenceName ?: return null
    val args = call.argumentList.expressions
    if (args.size < 2) return null

    val msgText = args[1].text
    val throwableText = args.getOrNull(2)?.text

    val newText = if (methodName == "e" && throwableText != null)
        "Timber.e($throwableText, $msgText)"
    else
        "Timber.$methodName($msgText)"

    return PsiElementFactory.getInstance(project).createExpressionFromText(newText, call)
}