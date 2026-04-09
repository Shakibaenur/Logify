package com.logify.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.logify.utils.PsiUtils
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Intention: place cursor on any variable → Alt+Enter → "Log this value"
 * Inserts a Log.d on the next line with correct indentation.
 *
 * Kotlin: Log.d("ClassName#method", "varName=$varName")
 * Java:   Log.d("ClassName#method", "varName=" + varName);
 */
class LogThisIntention : PsiElementBaseIntentionAction() {

    override fun getText(): String = "Log this value"
    override fun getFamilyName(): String = "Logify"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (editor == null) return false
        return resolveVarName(element) != null
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val varName = resolveVarName(element) ?: return
        val tag = PsiUtils.getLogContext(element).toTag()
        val isKotlin = PsiUtils.isKotlinFile(element)

        // Find the anchor statement to insert after
        val anchor = findAnchorStatement(element) ?: return
        val document = editor.document

        // Detect indentation of the anchor line
        val lineNumber = document.getLineNumber(anchor.textRange.startOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val indent = document.charsSequence
            .substring(lineStart, anchor.textRange.startOffset)
            .takeWhile { it == ' ' || it == '\t' }

        val logCall = if (isKotlin)
            "Log.d(\"$tag\", \"$varName=\$$varName\")"
        else
            "Log.d(\"$tag\", \"$varName=\" + $varName);"

        document.insertString(anchor.textRange.endOffset, "\n$indent$logCall")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    // ── Variable name resolution ───────────────────────────────────────────────
    // Works on: variable references, val/var declarations, function parameters

    private fun resolveVarName(element: PsiElement): String? {
        // Kotlin: reference usage  →  KtNameReferenceExpression
        val ktRef = element as? KtNameReferenceExpression
            ?: element.parent as? KtNameReferenceExpression
        if (ktRef != null) {
            // Must be inside a function body block
            if (PsiTreeUtil.getParentOfType(ktRef, KtBlockExpression::class.java) != null)
                return ktRef.text
        }

        // Kotlin: val/var declaration  →  KtProperty
        val ktProp = element.parent as? KtProperty
            ?: element.parent?.parent as? KtProperty
        if (ktProp != null && PsiTreeUtil.getParentOfType(ktProp, KtBlockExpression::class.java) != null)
            return ktProp.name

        // Kotlin: function parameter  →  KtParameter
        val ktParam = element.parent as? KtParameter
            ?: element.parent?.parent as? KtParameter
        if (ktParam != null) return ktParam.name

        val javaId = element as? PsiIdentifier ?: element.parent as? PsiIdentifier

        // Java: local variable declaration — String user = "..."
        if (javaId != null) {
            val localVar = javaId.parent as? PsiLocalVariable
            if (localVar != null && PsiTreeUtil.getParentOfType(localVar, PsiCodeBlock::class.java) != null)
                return localVar.name
        }

        // Java: method parameter — void login(String password)
        if (javaId != null) {
            val param = javaId.parent as? PsiParameter
            if (param != null) return param.name
        }

        // Java: variable reference — Log.d(TAG, user)
        if (javaId != null) {
            val ref = javaId.parent as? PsiReferenceExpression
            if (ref != null && PsiTreeUtil.getParentOfType(ref, PsiCodeBlock::class.java) != null)
                return ref.referenceName
        }

        return null
    }

    // ── Anchor statement (insert log after this) ──────────────────────────────

    private fun findAnchorStatement(element: PsiElement): PsiElement? {
        // Kotlin: walk up to the direct child of a KtBlockExpression
        val block = PsiTreeUtil.getParentOfType(element, KtBlockExpression::class.java)
        if (block != null) {
            return generateSequence<PsiElement>(element) { it.parent }
                .firstOrNull { it.parent == block }
        }
        // Java: walk up to a PsiStatement inside a code block
        return PsiTreeUtil.getParentOfType(element, PsiStatement::class.java)
    }
}