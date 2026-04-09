package com.logify.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile

/**
 * Right-click action: inserts top-level extension functions that wrap
 * android.util.Log calls using the receiver's simple class name as the tag.
 *
 * Insertion point: right after the last import (or package declaration).
 */
class KotlinWrapperGeneratorAction : AnAction("Insert Kotlin Log Wrapper") {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(CommonDataKeys.PSI_FILE)?.name?.endsWith(".kt") == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile  = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return
        val doc     = PsiDocumentManager.getInstance(project).getDocument(ktFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Insert Kotlin Log Wrapper", null, {
            try {
                val insertOffset = insertionOffset(ktFile)
                doc.insertString(insertOffset, WRAPPER_CODE)
                PsiDocumentManager.getInstance(project).commitDocument(doc)
            } catch (ex: Exception) {
                // Never crash the IDE
            }
        })
    }

    private fun insertionOffset(file: KtFile): Int =
        file.importList?.textRange?.endOffset
            ?: file.packageDirective?.textRange?.endOffset
            ?: 0

    companion object {
        private val WRAPPER_CODE = """


// ── Logify wrappers ──────────────────────────────────────────────────────────
fun Any.logd(message: String) {
    android.util.Log.d(this::class.simpleName ?: "Unknown", message)
}

fun Any.loge(message: String) {
    android.util.Log.e(this::class.simpleName ?: "Unknown", message)
}

fun Any.logi(message: String) {
    android.util.Log.i(this::class.simpleName ?: "Unknown", message)
}

fun Any.logw(message: String) {
    android.util.Log.w(this::class.simpleName ?: "Unknown", message)
}
// ─────────────────────────────────────────────────────────────────────────────

"""
    }
}
