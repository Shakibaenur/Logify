package com.loglens.macro

import com.intellij.codeInsight.template.*
import com.intellij.openapi.diagnostic.Logger
import com.loglens.context.LogContextDetector


class SmartDefaultMessageMacro : Macro() {

    companion object {
        private val LOG = Logger.getInstance(SmartDefaultMessageMacro::class.java)
    }

    override fun getName(): String = "smartDefaultMessageMacro"

    override fun getPresentableName(): String = "smartDefaultMessage()"

    override fun calculateResult(
        params: Array<out Expression>,
        context: ExpressionContext
    ): Result {
        return try {
            val element = context.psiElementAtStartOffset ?: return TextResult("Unknown")

            val logContext = LogContextDetector.detect(element)

            val message = when (logContext.lifecycleName) {
                "onCreate" -> "🟢 onCreate"
                "onStart" -> "▶️ onStart"
                "onResume" -> "⏯️ onResume"
                "onPause" -> "⏸️ onPause"
                "onStop" -> "⏹️ onStop"
                "onDestroy" -> "🔴 onDestroy"
                else -> when {
                    logContext.isCoroutine -> "⚡ coroutine"
                    logContext.isComposable -> "🎨 compose"
                    logContext.isLambda -> "lambda"
                    else -> ""
                }
            }

            TextResult(message)
        } catch (e: Exception) {
            LOG.warn("SmartDefaultMessageMacro failed", e)
            TextResult("")
        }
    }

    override fun isAcceptableInContext(
        context: com.intellij.codeInsight.template.TemplateContextType
    ): Boolean = true
}