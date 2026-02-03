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


        if (element.parent is MvPathType) return


        if (element is MvPath && element.path != null) return


        val fileText = element.containingFile.text
        if (fileText.startsWith("script {")) return


        if (element.parent is MvDotExpr) return


        val fileContent = element.containingFile.text
        val currentOffset = position.textRange.startOffset
        val specBlockStart = fileContent.lastIndexOf("spec", currentOffset)
        val specBlockEnd = fileContent.indexOf("}", specBlockStart)

        if (specBlockStart != -1 && (specBlockEnd == -1 || currentOffset < specBlockEnd)) {

            return
        }


        val document = parameters.editor?.document
        var hasParens = false
        if (document != null) {
            val endOffset = position.textRange.endOffset
            if (endOffset + 2 <= document.textLength) {
                hasParens = document.getText(TextRange(endOffset, endOffset + 2)) == "()"
            }
        }


        if (msl) {

            for (functionName in SPEC_BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {

                            if (functionName == "global") {
                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            } else {

                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {

                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        } else {

            for (functionName in BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {

                            val hasFollowingAngleBracket = ctx.selectionEndOffset < ctx.document.charsSequence.length &&
                                ctx.document.charsSequence[ctx.selectionEndOffset] == '<'

                            if (!hasFollowingAngleBracket) {

                                if (functionName.startsWith("borrow_global")) {
                                    ctx.document.insertString(ctx.selectionEndOffset, "<>()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                } else {

                                    ctx.document.insertString(ctx.selectionEndOffset, "()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                }
                            } else {

                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {

                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        }
    }
}