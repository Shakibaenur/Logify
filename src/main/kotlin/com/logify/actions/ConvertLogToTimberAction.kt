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
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.logify.inspections.buildTimberJavaExpr
import com.logify.inspections.buildTimberKotlinExpr
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Scans the entire project for Log.d/e/i/w/v calls and converts each one
 * to the equivalent Timber call. Runs with a progress indicator.
 *
 * Conversion rules:
 *   Log.d(TAG, msg)             → Timber.d(msg)
 *   Log.e(TAG, msg)             → Timber.e(msg)
 *   Log.e(TAG, msg, throwable)  → Timber.e(throwable, msg)   ← args swapped
 */
class ConvertLogToTimberAction : AnAction("Convert All Log Calls to Timber") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Logify: Converting Log calls to Timber…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val files = collectSourceFiles(project)
                var converted = 0

                files.forEachIndexed { index, vFile ->
                    if (indicator.isCanceled) return
                    indicator.fraction = index.toDouble() / files.size
                    indicator.text = vFile.name

                    val psiFile = PsiManager.getInstance(project).findFile(vFile)
                        ?: return@forEachIndexed

                    WriteCommandAction.runWriteCommandAction(project, "Convert Log to Timber", null, {
                        try {
                            when {
                                vFile.name.endsWith(".kt") -> converted += convertKotlin(project, psiFile)
                                vFile.name.endsWith(".java") -> converted += convertJava(project, psiFile)
                            }
                        } catch (_: Exception) {}
                    })
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        project,
                        "Converted $converted Log call(s) to Timber.\n\nRun 'Optimize Imports' to update import statements.",
                        "Logify — Done"
                    )
                }
            }
        })
    }

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun convertKotlin(project: Project, file: com.intellij.psi.PsiFile): Int {
        val calls = PsiTreeUtil.findChildrenOfType(file, KtDotQualifiedExpression::class.java)
            .filter { isKotlinLogCall(it) }

        for (call in calls) {
            val newExpr = buildTimberKotlinExpr(project, call) ?: continue
            call.replace(newExpr)
        }
        return calls.size
    }

    private fun isKotlinLogCall(expr: KtDotQualifiedExpression): Boolean {
        val receiver = expr.receiverExpression.text
        val call = expr.selectorExpression as? KtCallExpression ?: return false
        val method = (call.calleeExpression as? KtNameReferenceExpression)?.text ?: return false
        return receiver == "Log" && method in LOG_METHODS
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun convertJava(project: Project, file: com.intellij.psi.PsiFile): Int {
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
            .filter { isJavaLogCall(it) }

        for (call in calls) {
            val newExpr = buildTimberJavaExpr(project, call) ?: continue
            call.replace(newExpr)
        }
        return calls.size
    }

    private fun isJavaLogCall(expr: PsiMethodCallExpression): Boolean {
        val ref = expr.methodExpression
        return ref.qualifierExpression?.text == "Log" && ref.referenceName in LOG_METHODS
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

    companion object {
        private val LOG_METHODS = setOf("d", "e", "i", "w", "v")
    }
}