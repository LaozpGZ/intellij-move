package org.sui.lang.core.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.VisibleForTesting
import org.sui.ide.annotator.BUILTIN_FUNCTIONS
import org.sui.ide.annotator.SPEC_BUILTIN_FUNCTIONS
import org.sui.lang.core.completion.CompletionContext
import org.sui.lang.core.completion.BUILTIN_ITEM_PRIORITY
import org.sui.lang.core.completion.getOriginalOrSelf
import org.sui.lang.core.completion.safeGetOriginalOrSelf
import org.sui.lang.core.psi.MvPathType
import org.sui.lang.core.psi.MvPathExpr
import org.sui.lang.core.psi.MvDotExpr
import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.ext.isMsl
import org.sui.lang.core.psiElement
import org.sui.lang.core.resolve.collectCompletionVariants
import org.sui.lang.core.resolve.ref.MvReferenceElement
import org.sui.lang.core.resolve2.ref.ResolutionContext

object CommonCompletionProvider : MvCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement().withParent(psiElement<MvReferenceElement>())

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Use original position if possible to re-use caches of the real file
        val position = parameters.position.safeGetOriginalOrSelf()
        val element = position.parent as MvReferenceElement
        if (position !== element.referenceNameElement) return

        val msl = element.isMsl()

        // 检查是否在类型位置
        if (element.parent is MvPathType) return

        // 检查是否在限定路径中（路径有前缀）
        if (element is MvPath && element.path != null) return

        // 检查是否在脚本中
        val fileText = element.containingFile.text
        if (fileText.startsWith("script {")) return

        // 检查是否在 dot access 位置
        if (element.parent is MvDotExpr) return

        // 检查是否在 spec 块中（避免在 spec 块中显示不相关的函数补全）
        val fileContent = element.containingFile.text
        val currentOffset = position.textRange.startOffset
        val specBlockStart = fileContent.lastIndexOf("spec", currentOffset)
        val specBlockEnd = fileContent.indexOf("}", specBlockStart)

        if (specBlockStart != -1 && (specBlockEnd == -1 || currentOffset < specBlockEnd)) {
            // 在 spec 块中不提供内置函数补全，只保留关键字补全
            return
        }

        // 检查是否已经包含括号
        val document = parameters.editor?.document
        var hasParens = false
        if (document != null) {
            val endOffset = position.textRange.endOffset
            if (endOffset + 2 <= document.textLength) {
                hasParens = document.getText(TextRange(endOffset, endOffset + 2)) == "()"
            }
        }

        // 检查是否在 MSL 范围内
        if (msl) {
            // 为 MSL 范围内的代码提供 SPEC_BUILTIN_FUNCTIONS 补全
            for (functionName in SPEC_BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {
                            // 对于 global 函数，不添加类型参数
                            if (functionName == "global") {
                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            } else {
                                // 其他函数按正常逻辑处理
                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {
                            // 如果已经有括号，将光标移动到括号之间
                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        } else {
            // 为普通代码提供 BUILTIN_FUNCTIONS 补全
            for (functionName in BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {
                            // 检查后面是否紧跟 < 字符，如果是，就不添加 ()
                            val hasFollowingAngleBracket = ctx.selectionEndOffset < ctx.document.charsSequence.length &&
                                ctx.document.charsSequence[ctx.selectionEndOffset] == '<'

                            if (!hasFollowingAngleBracket) {
                                // 对于 borrow_global 系列函数，需要添加类型参数括号
                                if (functionName.startsWith("borrow_global")) {
                                    ctx.document.insertString(ctx.selectionEndOffset, "<>()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                } else {
                                    // 其他函数只添加 ()
                                    ctx.document.insertString(ctx.selectionEndOffset, "()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                }
                            } else {
                                // 如果后面紧跟 < 字符，将光标移动到 < 字符之后
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {
                            // 如果已经有括号，将光标移动到括号之间
                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        }
    }
}