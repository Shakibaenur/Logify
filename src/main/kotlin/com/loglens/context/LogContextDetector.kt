package com.loglens.context

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents

data class LogContext(
    val className: String?,
    val functionName: String?,
    val isComposable: Boolean,
    val isLambda: Boolean,
    val isCoroutine: Boolean,
    val lifecycleName: String?
)

object LogContextDetector {

    private val lifecycleMethods = setOf(
        "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"
    )

    private val coroutineBuilders = setOf(
        "launch", "async", "withContext"
    )

    fun detect(element: PsiElement): LogContext {
        var className: String? = null
        var functionName: String? = null
        var isComposable = false
        var isLambda = false
        var isCoroutine = false
        var lifecycleName: String? = null

        for (parent in element.parents(withSelf = true)) {
            when (parent) {
                is KtNamedFunction -> {
                    if (functionName == null) {
                        functionName = parent.name
                        if (functionName in lifecycleMethods) {
                            lifecycleName = functionName
                        }
                    }

                    if (!isComposable && parent.annotationEntries.any {
                            it.shortName?.asString() == "Composable"
                        }) {
                        isComposable = true
                    }
                }

                is KtClassOrObject -> {
                    if (className == null) {
                        className = parent.name
                    }
                }

                is KtLambdaExpression -> {
                    isLambda = true
                }

                is KtCallExpression -> {
                    val callee = parent.calleeExpression?.text
                    if (!isCoroutine && callee in coroutineBuilders) {
                        isCoroutine = true
                    }
                }

                is PsiMethod -> {
                    if (functionName == null) {
                        functionName = parent.name
                        if (functionName in lifecycleMethods) {
                            lifecycleName = functionName
                        }
                    }
                }

                is PsiClass -> {
                    if (className == null) {
                        className = parent.name
                    }
                }
            }
        }

        return LogContext(
            className = className,
            functionName = functionName,
            isComposable = isComposable,
            isLambda = isLambda,
            isCoroutine = isCoroutine,
            lifecycleName = lifecycleName
        )
    }

    fun defaultMessage(context: LogContext): String {
        return when {
            context.lifecycleName == "onCreate" -> "🟢 onCreate"
            context.lifecycleName == "onStart" -> "▶️ onStart"
            context.lifecycleName == "onResume" -> "⏯️ onResume"
            context.lifecycleName == "onPause" -> "⏸️ onPause"
            context.lifecycleName == "onStop" -> "⏹️ onStop"
            context.lifecycleName == "onDestroy" -> "🔴 onDestroy"
            context.isCoroutine -> "⚡ coroutine"
            context.isComposable -> "🎨 compose"
            context.isLambda -> "lambda"
            else -> ""
        }
    }
}