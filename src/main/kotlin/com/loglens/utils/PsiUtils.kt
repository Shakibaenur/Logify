package com.loglens.utils

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration

data class LogContext(val className: String, val methodName: String) {
    fun toTag(): String = if (methodName.isNotEmpty()) "$className#$methodName" else className
}

object PsiUtils {

    fun getLogContext(element: PsiElement): LogContext = try {
        if (isKotlinFile(element)) getKotlinContext(element) else getJavaContext(element)
    } catch (e: Exception) {
        LogContext("Unknown", "")
    }

    fun isKotlinFile(element: PsiElement): Boolean =
        element.containingFile?.name?.endsWith(".kt") == true

    // ── Kotlin ────────────────────────────────────────────────────────────────

    private fun getKotlinContext(element: PsiElement): LogContext {
        val methodName = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)?.name ?: ""
        val className  = getKotlinClassName(element)
        return LogContext(className, methodName)
    }

    fun getKotlinClassName(element: PsiElement): String {
        val fileName = (element.containingFile as? KtFile)?.name?.removeSuffix(".kt") ?: "Unknown"
        val ktClass  = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
        return if (ktClass != null) buildKotlinClassName(ktClass) else fileName
    }

    private fun buildKotlinClassName(ktClass: KtClass): String {
        val parts = mutableListOf<String>()
        var cur: KtClass? = ktClass
        while (cur != null) {
            parts.add(0, cur.name ?: "Anonymous")
            cur = PsiTreeUtil.getParentOfType(cur, KtClass::class.java)
        }
        return parts.joinToString(".")
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private fun getJavaContext(element: PsiElement): LogContext {
        val methodName = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)?.name ?: ""
        val className  = getJavaClassName(element)
        return LogContext(className, methodName)
    }

    fun getJavaClassName(element: PsiElement): String {
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        return if (psiClass != null) buildJavaClassName(psiClass)
        else element.containingFile?.name?.removeSuffix(".java") ?: "Unknown"
    }

    private fun buildJavaClassName(psiClass: PsiClass): String {
        val parts = mutableListOf<String>()
        var cur: PsiClass? = psiClass
        while (cur?.name != null) {
            parts.add(0, cur.name!!)
            cur = PsiTreeUtil.getParentOfType(cur, PsiClass::class.java)
        }
        return parts.joinToString(".")
    }
}
