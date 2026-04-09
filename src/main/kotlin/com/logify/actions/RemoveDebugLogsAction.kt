package com.logify.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Removes all debug-level Log/Timber calls (d, i, v) project-wide.
 * Also removes any now-empty if (BuildConfig.DEBUG) { } guards left behind.
 *
 * Removed:  Log.d / Log.i / Log.v / Timber.d / Timber.i / Timber.v
 * Kept:     Log.e / Log.w / Timber.e / Timber.w
 */
class RemoveDebugLogsAction : AnAction("Remove Debug Log Calls") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val confirm = Messages.showOkCancelDialog(
            project,
            "This will permanently delete all Log.d / Log.i / Log.v / Timber.d / Timber.i / Timber.v calls " +
                    "and their wrapping if (BuildConfig.DEBUG) blocks from the project.\n\n" +
                    "Log.e and Log.w calls will be kept.\n\nProceed?",
            "Logify — Remove Debug Logs",
            "Remove",
            "Cancel",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.OK) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Logify: Removing debug log calls…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val files = collectSourceFiles(project)
                var removed = 0

                files.forEachIndexed { index, vFile ->
                    if (indicator.isCanceled) return
                    indicator.fraction = index.toDouble() / files.size
                    indicator.text = vFile.name

                    val psiFile = PsiManager.getInstance(project).findFile(vFile)
                        ?: return@forEachIndexed

                    WriteCommandAction.runWriteCommandAction(project, "Remove Debug Logs", null, {
                        try {
                            when {
                                vFile.name.endsWith(".kt") -> removed += removeFromKotlin(psiFile)
                                vFile.name.endsWith(".java") -> removed += removeFromJava(psiFile)
                            }
                        } catch (_: Exception) {}
                    })
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        project,
                        "Removed $removed debug log call(s) and any empty BuildConfig.DEBUG guards.\n\nLog.e and Log.w calls were kept.",
                        "Logify — Done"
                    )
                }
            }
        })
    }

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun removeFromKotlin(file: com.intellij.psi.PsiFile): Int {
        val calls = PsiTreeUtil.findChildrenOfType(file, KtDotQualifiedExpression::class.java)
            .filter { isDebugLogCall(it.receiverExpression.text, getKotlinMethod(it)) }

        // Determine what to actually delete for each call:
        // - If the call is the only statement inside if (BuildConfig.DEBUG) → delete the whole if
        // - If the call is inside a braces block (shared with other statements) → delete just the call
        // - If the call is the direct then-branch (no braces) of a debug guard → delete the whole if
        val toDelete = mutableListOf<PsiElement>()
        for (expr in calls) {
            val target = resolveKotlinDeletionTarget(expr)
            if (target != null) toDelete.add(target)
        }

        var count = 0
        for (element in toDelete.distinct()) {
            try {
                if (!element.isValid) continue
                element.delete()
                count++
            } catch (_: Exception) {}
        }

        // Second pass: remove any now-empty if (BuildConfig.DEBUG) { } blocks
        cleanupEmptyKotlinGuards(file)

        return count
    }

    private fun resolveKotlinDeletionTarget(expr: KtDotQualifiedExpression): PsiElement? {
        val parent = expr.parent

        // Case 1: Log call is the direct then-branch (no braces): if (BuildConfig.DEBUG) Log.d(...)
        if (parent is KtIfExpression && isDebugGuard(parent.condition?.text)) {
            return if (parent.parent is KtBlockExpression) parent else null
        }

        // Case 2: Log call is inside a { } block
        if (parent is KtBlockExpression) {
            val ifExpr = parent.parent as? KtIfExpression
            if (ifExpr != null && isDebugGuard(ifExpr.condition?.text)) {
                // Only statement in the guard → delete the whole if
                val nonDebugStatements = parent.statements.filter { stmt ->
                    !(stmt is KtDotQualifiedExpression && isDebugLogCall(stmt.receiverExpression.text, getKotlinMethod(stmt)))
                }
                if (nonDebugStatements.isEmpty() && ifExpr.parent is KtBlockExpression) {
                    return ifExpr
                }
            }
            // Regular block (not a debug guard, or has other statements) → delete just the call
            return expr
        }

        return null
    }

    private fun cleanupEmptyKotlinGuards(file: com.intellij.psi.PsiFile) {
        val emptyGuards = PsiTreeUtil.findChildrenOfType(file, KtIfExpression::class.java)
            .filter { ifExpr ->
                isDebugGuard(ifExpr.condition?.text) &&
                        ifExpr.`else` == null &&
                        (ifExpr.then as? KtBlockExpression)?.statements?.isEmpty() == true &&
                        ifExpr.parent is KtBlockExpression
            }
        for (guard in emptyGuards) {
            try {
                if (guard.isValid) guard.delete()
            } catch (_: Exception) {}
        }
    }

    private fun getKotlinMethod(expr: KtDotQualifiedExpression): String? {
        val call = expr.selectorExpression as? KtCallExpression ?: return null
        return (call.calleeExpression as? KtNameReferenceExpression)?.text
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun removeFromJava(file: com.intellij.psi.PsiFile): Int {
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
            .filter {
                val qualifier = it.methodExpression.qualifierExpression?.text ?: return@filter false
                val method = it.methodExpression.referenceName ?: return@filter false
                isDebugLogCall(qualifier, method)
            }

        val toDelete = mutableListOf<PsiElement>()
        for (call in calls) {
            val target = resolveJavaDeletionTarget(call)
            if (target != null) toDelete.add(target)
        }

        var count = 0
        for (element in toDelete.distinct()) {
            try {
                if (!element.isValid) continue
                element.delete()
                count++
            } catch (_: Exception) {}
        }

        // Second pass: remove any now-empty if (BuildConfig.DEBUG) { } blocks
        cleanupEmptyJavaGuards(file)

        return count
    }

    private fun resolveJavaDeletionTarget(call: PsiMethodCallExpression): PsiElement? {
        val exprStatement = call.parent as? PsiExpressionStatement ?: return null
        val ifStmt = exprStatement.parent as? PsiIfStatement
            ?: (exprStatement.parent as? PsiBlockStatement)?.let {
                it.parent as? PsiIfStatement
            }

        if (ifStmt != null && isDebugGuard(ifStmt.condition?.text)) {
            val block = ifStmt.thenBranch as? PsiBlockStatement
            if (block != null) {
                // Check if all statements in block are debug log calls
                val nonDebugStatements = block.codeBlock.statements.filter { stmt ->
                    val innerCall = (stmt as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    innerCall == null || !isDebugLogCall(
                        innerCall.methodExpression.qualifierExpression?.text ?: "",
                        innerCall.methodExpression.referenceName
                    )
                }
                if (nonDebugStatements.isEmpty()) return ifStmt
            } else {
                // No braces: if (BuildConfig.DEBUG) Log.d(...);
                return ifStmt
            }
        }

        return exprStatement
    }

    private fun cleanupEmptyJavaGuards(file: com.intellij.psi.PsiFile) {
        val emptyGuards = PsiTreeUtil.findChildrenOfType(file, PsiIfStatement::class.java)
            .filter { ifStmt ->
                isDebugGuard(ifStmt.condition?.text) &&
                        ifStmt.elseBranch == null &&
                        (ifStmt.thenBranch as? PsiBlockStatement)?.codeBlock?.statements?.isEmpty() == true
            }
        for (guard in emptyGuards) {
            try {
                if (guard.isValid) guard.delete()
            } catch (_: Exception) {}
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private fun isDebugLogCall(qualifier: String, method: String?): Boolean =
        qualifier in LOG_RECEIVERS && method in DEBUG_METHODS

    private fun isDebugGuard(conditionText: String?): Boolean =
        conditionText != null &&
                (conditionText.contains("BuildConfig.DEBUG") || conditionText.contains("BuildConfig.BUILD_TYPE"))

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
        private val LOG_RECEIVERS = setOf("Log", "Timber")
        private val DEBUG_METHODS = setOf("d", "i", "v")
    }
}
