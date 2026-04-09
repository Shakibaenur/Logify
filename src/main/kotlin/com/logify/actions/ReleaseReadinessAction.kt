package com.logify.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.logify.inspections.SensitiveDataInLogInspection
import com.logify.inspections.UnguardedLogInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Scans the entire project and produces a release readiness report covering:
 *  - Debug log calls (d / i / v) that will appear in release builds
 *  - Unguarded log calls not wrapped in BuildConfig.DEBUG
 *  - Log calls that may expose sensitive data
 */
class ReleaseReadinessAction : AnAction("Check Release Readiness") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Logify: Scanning for release issues…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val files = collectSourceFiles(project)
                val issues = mutableListOf<Issue>()

                files.forEachIndexed { index, vFile ->
                    if (indicator.isCanceled) return
                    indicator.fraction = index.toDouble() / files.size
                    indicator.text = vFile.name

                    val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEachIndexed

                    com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                        try {
                            if (vFile.name.endsWith(".kt")) scanKotlin(psiFile, vFile, issues)
                            else scanJava(psiFile, vFile, issues)
                        } catch (_: Exception) {}
                    }
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    showReport(project, issues)
                }
            }
        })
    }

    // ── Kotlin scanner ────────────────────────────────────────────────────────

    private fun scanKotlin(file: com.intellij.psi.PsiFile, vFile: VirtualFile, issues: MutableList<Issue>) {
        PsiTreeUtil.findChildrenOfType(file, KtDotQualifiedExpression::class.java).forEach { expr ->
            val receiver = expr.receiverExpression.text
            val call = expr.selectorExpression as? KtCallExpression ?: return@forEach
            val method = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return@forEach
            if (receiver !in LOG_RECEIVERS || method !in ALL_METHODS) return@forEach

            val line = file.viewProvider.document?.getLineNumber(expr.textRange.startOffset)?.plus(1) ?: 0

            // Debug log
            if (method in DEBUG_METHODS) {
                issues += Issue(IssueType.DEBUG_LOG, vFile.name, line, "Timber/Log.$method() will appear in release")
            }

            // Unguarded log
            if (!isGuardedKotlin(expr)) {
                issues += Issue(IssueType.UNGUARDED_LOG, vFile.name, line, "$receiver.$method() not wrapped in BuildConfig.DEBUG")
            }

            // Sensitive data
            val sensitiveArg = call.valueArguments.firstOrNull {
                SensitiveDataInLogInspection.hasSensitiveContent(it.text)
            }
            if (sensitiveArg != null) {
                issues += Issue(IssueType.SENSITIVE_DATA, vFile.name, line, "May log sensitive data: ${sensitiveArg.text.take(40)}")
            }
        }
    }

    private fun isGuardedKotlin(element: com.intellij.psi.PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is KtIfExpression) {
                val cond = parent.condition?.text ?: ""
                if (cond.contains("BuildConfig.DEBUG") || cond.contains("BuildConfig.BUILD_TYPE")) return true
            }
            parent = parent.parent
        }
        return false
    }

    // ── Java scanner ──────────────────────────────────────────────────────────

    private fun scanJava(file: com.intellij.psi.PsiFile, vFile: VirtualFile, issues: MutableList<Issue>) {
        PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).forEach { expr ->
            val ref = expr.methodExpression
            val qualifier = ref.qualifierExpression?.text ?: return@forEach
            val method = ref.referenceName ?: return@forEach
            if (qualifier !in LOG_RECEIVERS || method !in ALL_METHODS) return@forEach

            val line = file.viewProvider.document?.getLineNumber(expr.textRange.startOffset)?.plus(1) ?: 0

            // Debug log
            if (method in DEBUG_METHODS) {
                issues += Issue(IssueType.DEBUG_LOG, vFile.name, line, "$qualifier.$method() will appear in release")
            }

            // Unguarded log
            if (!isGuardedJava(expr)) {
                issues += Issue(IssueType.UNGUARDED_LOG, vFile.name, line, "$qualifier.$method() not wrapped in BuildConfig.DEBUG")
            }

            // Sensitive data
            val sensitiveArg = expr.argumentList.expressions.firstOrNull {
                SensitiveDataInLogInspection.hasSensitiveContent(it.text)
            }
            if (sensitiveArg != null) {
                issues += Issue(IssueType.SENSITIVE_DATA, vFile.name, line, "May log sensitive data: ${sensitiveArg.text.take(40)}")
            }
        }
    }

    private fun isGuardedJava(element: com.intellij.psi.PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is PsiIfStatement) {
                val cond = parent.condition?.text ?: ""
                if (cond.contains("BuildConfig.DEBUG") || cond.contains("BuildConfig.BUILD_TYPE")) return true
            }
            parent = parent.parent
        }
        return false
    }

    // ── Report dialog ─────────────────────────────────────────────────────────

    private fun showReport(project: Project, issues: List<Issue>) {
        val debugCount = issues.count { it.type == IssueType.DEBUG_LOG }
        val unguardedCount = issues.count { it.type == IssueType.UNGUARDED_LOG }
        val sensitiveCount = issues.count { it.type == IssueType.SENSITIVE_DATA }
        val total = issues.size

        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════════════════════╗")
        sb.appendLine("║        LOGIFY RELEASE READINESS          ║")
        sb.appendLine("╚══════════════════════════════════════════╝")
        sb.appendLine()

        if (total == 0) {
            sb.appendLine("✅  No issues found — your project looks release-ready!")
        } else {
            sb.appendLine("Found $total issue(s):")
            sb.appendLine()
            sb.appendLine("🔴  Sensitive data leaks : $sensitiveCount")
            sb.appendLine("⚠️   Unguarded log calls  : $unguardedCount")
            sb.appendLine("📋  Debug log calls       : $debugCount")
            sb.appendLine()
            sb.appendLine("─────────────────────────────────────────")

            val grouped = issues.groupBy { it.fileName }
            grouped.forEach { (fileName, fileIssues) ->
                sb.appendLine()
                sb.appendLine("📄 $fileName")
                fileIssues.sortedBy { it.line }.forEach { issue ->
                    val icon = when (issue.type) {
                        IssueType.SENSITIVE_DATA -> "🔴"
                        IssueType.UNGUARDED_LOG -> "⚠️ "
                        IssueType.DEBUG_LOG -> "📋"
                    }
                    sb.appendLine("   $icon Line ${issue.line}: ${issue.message}")
                }
            }

            sb.appendLine()
            sb.appendLine("─────────────────────────────────────────")
            sb.appendLine()
            sb.appendLine("💡 Quick fixes:")
            if (sensitiveCount > 0) sb.appendLine("   • Remove log calls containing passwords/tokens/secrets")
            if (unguardedCount > 0) sb.appendLine("   • Tools → Wrap All Unguarded Logs in Project")
            if (debugCount > 0)     sb.appendLine("   • Tools → Remove Debug Log Calls")
        }

        val textArea = JTextArea(sb.toString()).apply {
            isEditable = false
            font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
            caretPosition = 0
        }

        val dialog = DialogBuilder(project)
        dialog.setTitle("Logify — Release Readiness Report")
        dialog.setCenterPanel(JScrollPane(textArea).apply {
            preferredSize = java.awt.Dimension(600, 450)
        })
        dialog.addOkAction()
        dialog.show()
    }

    // ── File collection ───────────────────────────────────────────────────────

    private fun collectSourceFiles(project: Project): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val roots = ProjectRootManager.getInstance(project).contentSourceRoots
            .ifEmpty { ProjectRootManager.getInstance(project).contentRoots }
        roots.forEach { collectRecursive(it, result) }
        return result
    }

    private fun collectRecursive(dir: VirtualFile, result: MutableList<VirtualFile>) {
        for (child in dir.children) {
            when {
                child.isDirectory -> collectRecursive(child, result)
                child.name.endsWith(".kt") || child.name.endsWith(".java") -> result.add(child)
            }
        }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    private data class Issue(
        val type: IssueType,
        val fileName: String,
        val line: Int,
        val message: String
    )

    private enum class IssueType { DEBUG_LOG, UNGUARDED_LOG, SENSITIVE_DATA }

    companion object {
        private val LOG_RECEIVERS = setOf("Log", "Timber")
        private val DEBUG_METHODS = setOf("d", "i", "v")
        private val ALL_METHODS = setOf("d", "e", "i", "w", "v")
    }
}
