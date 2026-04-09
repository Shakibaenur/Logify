package com.logify.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Inspection: warns when a Log or Timber call contains arguments whose text
 * matches known sensitive data keywords (password, token, secret, etc.).
 *
 * Example triggers:
 *   Log.d(TAG, "password=$password")
 *   Log.d(TAG, "token=" + authToken)
 *   Timber.d("user email: $userEmail")
 */
class SensitiveDataInLogInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Sensitive data in log call"
    override fun getGroupDisplayName(): String = "Logify"
    override fun getShortName(): String = "SensitiveDataInLog"
    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (holder.file.name.endsWith(".kt")) kotlinVisitor(holder)
        else javaVisitor(holder)

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun javaVisitor(holder: ProblemsHolder) = object : JavaElementVisitor() {
        override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
            try {
                val ref = expr.methodExpression
                val qualifier = ref.qualifierExpression?.text ?: return
                val method = ref.referenceName ?: return
                if (qualifier !in LOG_RECEIVERS || method !in LOG_METHODS) return

                val sensitiveArg = expr.argumentList.expressions
                    .firstOrNull { hasSensitiveContent(it.text) } ?: return

                holder.registerProblem(
                    sensitiveArg,
                    "Log call may expose sensitive data — remove before release",
                    ProblemHighlightType.WARNING
                )
            } catch (_: Exception) {}
        }
    }

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun kotlinVisitor(holder: ProblemsHolder) = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            try {
                val expr = element as? KtDotQualifiedExpression ?: return
                val receiver = expr.receiverExpression.text
                val call = expr.selectorExpression as? KtCallExpression ?: return
                val method = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return
                if (receiver !in LOG_RECEIVERS || method !in LOG_METHODS) return

                val sensitiveArg = call.valueArguments
                    .firstOrNull { hasSensitiveContent(it.text) } ?: return

                holder.registerProblem(
                    sensitiveArg,
                    "Log call may expose sensitive data — remove before release",
                    ProblemHighlightType.WARNING
                )
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val LOG_RECEIVERS = setOf("Log", "Timber")
        private val LOG_METHODS = setOf("d", "e", "i", "w", "v")

        val SENSITIVE_KEYWORDS = setOf(
            "password", "passwd", "pwd",
            "token", "accesstoken", "refreshtoken", "authtoken", "bearertoken",
            "secret", "apikey", "apisecret",
            "email", "phone", "phonenumber",
            "ssn", "socialsecurity",
            "creditcard", "cardnumber", "cvv", "cvc",
            "privatekey", "sessionkey", "sessionid",
            "otp", "pincode"
        )

        fun hasSensitiveContent(text: String): Boolean {
            val normalized = text.lowercase().replace(Regex("[^a-z0-9]"), "")
            return SENSITIVE_KEYWORDS.any { normalized.contains(it) }
        }
    }
}
