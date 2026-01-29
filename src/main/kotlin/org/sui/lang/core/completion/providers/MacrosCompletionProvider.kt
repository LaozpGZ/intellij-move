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
import org.sui.lang.MvElementTypes
import org.sui.lang.core.MvPsiPattern
import org.sui.lang.core.completion.MACRO_PRIORITY
import org.sui.lang.core.psi.MvPath

object MacrosCompletionProvider : MvCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = MvPsiPattern.path()
            .andNot(MvPsiPattern.pathType())
            .andNot(MvPsiPattern.schemaLit())
            .andNot(
                PlatformPatterns.psiElement()
                    .afterLeaf(PlatformPatterns.psiElement(MvElementTypes.COLON_COLON))
            )

    data class MacroInfo(
        val name: String,
        val tailText: String,
        val typeText: String,
        val insertText: String = "()"
    )

    private val macros = listOf(
        MacroInfo("assert!", "(_: bool, err: u64)", "()"),
        MacroInfo("vector!", "([_])", "vector<T>"),
        MacroInfo("debug!", "(_: ...)", "()"),
        MacroInfo("option!", "(_)", "option<T>"),
        MacroInfo("result!", "(_, _)", "result<T, E>"),
        MacroInfo("bcs!", "(_)", "vector<u8>"),
        MacroInfo("object!", "(_)", "object"),
        MacroInfo("transfer!", "(_, _)", "()"),
        MacroInfo("event!", "(_)", "()"),
        MacroInfo("table!", "(_)", "table<K, V>"),
        MacroInfo("system!", "(_)", "()"),
        MacroInfo("vote!", "(_)", "()")
    )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val maybePath = parameters.position.parent
        val path = maybePath as? MvPath ?: maybePath.parent as MvPath

        if (parameters.position !== path.referenceNameElement) return

        macros.forEach { macro ->
            val lookupElement = LookupElementBuilder
                .create(macro.name)
                .withTailText(macro.tailText)
                .withTypeText(macro.typeText)
                .withInsertHandler { ctx, _ ->
                    val document = ctx.document
                    document.insertString(ctx.selectionEndOffset, macro.insertText)
                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                }
            result.addElement(PrioritizedLookupElement.withPriority(lookupElement, MACRO_PRIORITY))
        }
    }

}
