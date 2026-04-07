package com.loglens.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.loglens.utils.PsiUtils

/**
 * Live-template macro that resolves to just "ClassName" (no method part).
 * Used by the "logt" template to generate the TAG constant value.
 *
 * Usage in template XML:  expression="logClassNameMacro()"
 */
class LogClassNameMacro : Macro() {

    override fun getName(): String = "logClassNameMacro"
    override fun getPresentableName(): String = "logClassName()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        return try {
            val element = context.psiElementAtStartOffset ?: return TextResult("Unknown")
            val name = if (PsiUtils.isKotlinFile(element)) {
                PsiUtils.getKotlinClassName(element)
            } else {
                PsiUtils.getJavaClassName(element)
            }
            TextResult(name)
        } catch (e: Exception) {
            TextResult("Unknown")
        }
    }

    override fun isAcceptableInContext(context: com.intellij.codeInsight.template.TemplateContextType): Boolean = true
}
