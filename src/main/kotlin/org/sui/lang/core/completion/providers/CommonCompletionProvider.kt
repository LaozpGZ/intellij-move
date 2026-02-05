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
import org.sui.lang.core.psi.MvPat
import org.sui.lang.core.psi.ext.isMsl
import org.sui.lang.core.psi.ext.hasAncestor
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


        // Skip type positions.
        if (element.parent is MvPathType) return

        // Skip pattern positions (e.g. let bindings).
        if (element is MvPat || element.hasAncestor<MvPat>()) return

        // Skip qualified paths (already has a prefix).
        if (element is MvPath && element.path != null) return

        // Skip script context.
        val fileText = element.containingFile.text
        if (fileText.startsWith("script {")) return

        // Skip dot-access positions.
        if (element.parent is MvDotExpr) return


        val fileContent = element.containingFile.text
        val currentOffset = position.textRange.startOffset
        val specBlockStart = fileContent.lastIndexOf("spec", currentOffset)
        val specBlockEnd = fileContent.indexOf("}", specBlockStart)

        // Do not provide builtin completions inside spec blocks.
        if (specBlockStart != -1 && (specBlockEnd == -1 || currentOffset < specBlockEnd)) {
            return
        }


        val document = parameters.editor?.document
        var hasParens = false
        // Detect whether `()` already exists right after the name.
        if (document != null) {
            val endOffset = position.textRange.endOffset
            if (endOffset + 2 <= document.textLength) {
                hasParens = document.getText(TextRange(endOffset, endOffset + 2)) == "()"
            }
        }


        // Provide SPEC_BUILTIN_FUNCTIONS completions in MSL.
        if (msl) {
            for (functionName in SPEC_BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {
                            // `global` does not use type arguments.
                            if (functionName == "global") {
                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            } else {
                                // Default behavior for other functions.
                                ctx.document.insertString(ctx.selectionEndOffset, "()")
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {
                            // If parentheses already exist, just move inside them.
                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        } else {
            // Provide BUILTIN_FUNCTIONS completions in normal code.
            for (functionName in BUILTIN_FUNCTIONS) {
                val lookupElement = LookupElementBuilder
                    .create(functionName)
                    .withTypeText("builtin")
                    .withInsertHandler { ctx, _ ->
                        if (!hasParens) {
                            // If a type argument list already follows, do not add ().
                            val hasFollowingAngleBracket = ctx.selectionEndOffset < ctx.document.charsSequence.length &&
                                ctx.document.charsSequence[ctx.selectionEndOffset] == '<'

                            if (!hasFollowingAngleBracket) {
                                // borrow_global* requires type arguments.
                                if (functionName.startsWith("borrow_global")) {
                                    ctx.document.insertString(ctx.selectionEndOffset, "<>()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                } else {
                                    // Other functions only add ().
                                    ctx.document.insertString(ctx.selectionEndOffset, "()")
                                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                                }
                            } else {
                                // If a type argument list follows, move into it.
                                EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                            }
                        } else {
                            // If parentheses already exist, just move inside them.
                            EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                        }
                    }
                result.addElement(PrioritizedLookupElement.withPriority(lookupElement, BUILTIN_ITEM_PRIORITY))
            }
        }
    }
}
