package com.logify.macro

import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.macro.MacroBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class LogVariableMacro : MacroBase("nearestVariableMacro", "nearestVariableMacro()") {

    companion object {
        private val LOG = Logger.getInstance(LogVariableMacro::class.java)
    }

    override fun calculateResult(
        params: Array<out Expression>,
        context: ExpressionContext,
        quick: Boolean
    ): Result? {
        return try {
            val project = context.project ?: return TextResult("value")
            val editor = context.editor ?: return TextResult("value")
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                ?: return TextResult("value")

            val offset = context.startOffset
            val elementAtCaret = file.findElementAt(offset)
                ?: return TextResult("value")

            val candidate = findNearestVariableName(elementAtCaret, offset)
                ?: "value"

            TextResult(candidate)
        } catch (t: Throwable) {
            LOG.warn("nearestVariableMacro failed", t)
            TextResult("value")
        }
    }

    override fun isAcceptableInContext(context: TemplateContextType): Boolean {
        return true
    }

    private fun findNearestVariableName(anchor: PsiElement, offset: Int): String? {
        val scopeChain = generateSequence(anchor) { it.parent }

        for (scope in scopeChain) {
            val candidates = mutableListOf<VariableCandidate>()

            when (scope) {
                is KtNamedFunction -> {
                    // Kotlin parameters
                    scope.valueParameters
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }

                    // Kotlin local properties inside this function
                    PsiTreeUtil.findChildrenOfType(scope, KtProperty::class.java)
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }
                }

                is KtFile -> {
                    // Kotlin top-level properties
                    PsiTreeUtil.findChildrenOfType(scope, KtProperty::class.java)
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }
                }

                is PsiMethod -> {
                    // Java parameters
                    scope.parameterList.parameters
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }

                    // Java locals
                    PsiTreeUtil.findChildrenOfType(scope, PsiLocalVariable::class.java)
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }
                }

                is PsiClass -> {
                    // Java fields
                    scope.fields
                        .filter { it.textOffset <= offset }
                        .forEach { candidates += VariableCandidate(it.name, it.textOffset) }
                }
            }

            val best = candidates
                .filter { !it.name.isNullOrBlank() }
                .maxByOrNull { it.offset }

            if (best != null) return best.name
        }

        return null
    }

    private data class VariableCandidate(
        val name: String?,
        val offset: Int
    )
}