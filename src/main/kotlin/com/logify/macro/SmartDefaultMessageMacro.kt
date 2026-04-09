package com.logify.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.logify.context.LogContextDetector

/**
 * Live-template macro that suggests a smart default log message based on the
 * current context (lifecycle method, coroutine, composable, etc.).
 *
 * Usage in template XML:  expression="smartDefaultMessageMacro()"
 */
class SmartDefaultMessageMacro : Macro() {

    override fun getName(): String = "smartDefaultMessageMacro"
    override fun getPresentableName(): String = "smartDefaultMessage()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        return try {
            val element = context.psiElementAtStartOffset ?: return TextResult("")
            val logContext = LogContextDetector.detect(element)
            TextResult(LogContextDetector.defaultMessage(logContext))
        } catch (e: Exception) {
            TextResult("")
        }
    }

    override fun isAcceptableInContext(context: com.intellij.codeInsight.template.TemplateContextType): Boolean = true
}