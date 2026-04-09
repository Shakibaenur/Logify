package com.logify.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.logify.inspections.UnguardedLogInspection.Companion.LOG_METHODS
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Scans the entire project for unguarded Log calls and wraps each one
 * with if (BuildConfig.DEBUG) { ... }.  Runs with a progress indicator.
 */
class WrapAllUnguardedLogsAction : AnAction("Wrap All Unguarded Logs in Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Logify: Wrapping unguarded Log calls…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val files = collectSourceFiles(project)
                var fixed = 0

                files.forEachIndexed { index, vFile ->
                    if (indicator.isCanceled) return
                    indicator.fraction = index.toDouble() / files.size
                    indicator.text = vFile.name

                    val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEachIndexed

                    WriteCommandAction.runWriteCommandAction(project, "Wrap Unguarded Logs", null, {
                        try {
                            when {
                                vFile.name.endsWith(".kt") -> fixed += wrapKotlin(project, psiFile)
                                vFile.name.endsWith(".java") -> fixed += wrapJava(project, psiFile)
                            }
                        } catch (_: Exception) {}
                    })
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        project,
                        "Wrapped $fixed unguarded Log call(s) with BuildConfig.DEBUG.",
                        "Logify — Done"
                    )
                }
            }
        })
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun wrapJava(project: Project, file: com.intellij.psi.PsiFile): Int {
        val factory = PsiElementFactory.getInstance(project)
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
            .filter { isUnguardedJavaLogCall(it) }

        for (call in calls) {
            val statement = PsiTreeUtil.getParentOfType(call, PsiStatement::class.java) ?: continue
            val ifStatement = factory.createStatementFromText(
                "if (BuildConfig.DEBUG) { ${call.text}; }", call
            ) as? PsiIfStatement ?: continue
            statement.replace(ifStatement)
        }
        return calls.size
    }

    private fun isUnguardedJavaLogCall(expr: PsiMethodCallExpression): Boolean {
        val ref = expr.methodExpression
        if (ref.qualifierExpression?.text != "Log" || ref.referenceName !in LOG_METHODS) return false
        return !isInsideDebugGuard(expr)
    }

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun wrapKotlin(project: Project, file: com.intellij.psi.PsiFile): Int {
        val factory = KtPsiFactory(project)
        val calls = PsiTreeUtil.findChildrenOfType(file, KtDotQualifiedExpression::class.java)
            .filter { isUnguardedKotlinLogCall(it) }

        for (call in calls) {
            val statement = PsiTreeUtil.getParentOfType(call, KtExpression::class.java, false) ?: continue
            val ifExpr = factory.createExpression("if (BuildConfig.DEBUG) { ${call.text} }")
            statement.replace(ifExpr)
        }
        return calls.size
    }

    private fun isUnguardedKotlinLogCall(expr: KtDotQualifiedExpression): Boolean {
        val receiver = expr.receiverExpression.text
        val call = expr.selectorExpression as? KtCallExpression ?: return false
        val method = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return false
        if (receiver != "Log" || method !in LOG_METHODS) return false
        return !isInsideDebugGuard(expr)
    }

    // ── Guard detection ───────────────────────────────────────────────────────

    private fun isInsideDebugGuard(element: com.intellij.psi.PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            val condition = when (parent) {
                is PsiIfStatement -> parent.condition?.text ?: ""
                is KtIfExpression -> parent.condition?.text ?: ""
                else -> ""
            }
            if (condition.contains("BuildConfig.DEBUG") || condition.contains("BuildConfig.BUILD_TYPE")) return true
            parent = parent.parent
        }
        return false
    }

    // ── File collection ───────────────────────────────────────────────────────

    private fun collectSourceFiles(project: Project): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val roots = ProjectRootManager.getInstance(project).contentSourceRoots
            .ifEmpty { ProjectRootManager.getInstance(project).contentRoots }
        roots.forEach { root -> collectRecursive(root, result) }
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
}
