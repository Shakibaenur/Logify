package com.loglens.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.loglens.utils.PsiUtils

/**
 * Live-template macro that resolves to "ClassName#methodName" for the current
 * caret position in both Java and Kotlin files.
 *
 * Usage in template XML:  expression="logTagMacro()"
 */
class LogTagMacro : Macro() {

    override fun getName(): String = "logTagMacro"
    override fun getPresentableName(): String = "logTag()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        return try {
            val element = context.psiElementAtStartOffset ?: return TextResult("Unknown")
            TextResult(PsiUtils.getLogContext(element).toTag())
        } catch (e: Exception) {
            TextResult("Unknown")
        }
    }

    override fun isAcceptableInContext(context: com.intellij.codeInsight.template.TemplateContextType): Boolean = true
}
