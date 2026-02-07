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
import org.sui.cli.settings.moveLanguageFeatures
import org.sui.ide.utils.getSignature
import org.sui.lang.MvElementTypes
import org.sui.lang.core.MvPsiPattern
import org.sui.lang.core.completion.MACRO_PRIORITY
import org.sui.lang.core.macros.DefaultMacroSemanticService
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvMacroCallExpr
import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.ext.isMacro
import org.sui.lang.core.resolve.createProcessor
import org.sui.lang.core.resolve.isVisibleFrom
import org.sui.lang.core.resolve.ref.FUNCTIONS
import org.sui.lang.core.resolve2.processNestedScopesUpwards
import org.sui.lang.core.resolve2.ref.ResolutionContext
import org.sui.lang.core.resolve2.ref.resolveAliases

object MacrosCompletionProvider : MvCompletionProvider() {
    private val macroSemanticService = DefaultMacroSemanticService

    override val elementPattern: ElementPattern<out PsiElement>
        get() = MvPsiPattern.path()
            .andNot(MvPsiPattern.pathType())
            .andNot(MvPsiPattern.schemaLit())
            .andNot(
                PlatformPatterns.psiElement()
                    .afterLeaf(PlatformPatterns.psiElement(MvElementTypes.COLON_COLON))
            )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val maybePath = parameters.position.parent
        val path = maybePath as? MvPath ?: maybePath.parent as MvPath

        if (parameters.position !== path.referenceNameElement) return

        if (!path.project.moveLanguageFeatures.macroFunctions) return

        val seenNames = mutableSetOf<String>()
        val userMacros = collectUserMacroCandidates(path)
        userMacros.forEach { candidate ->
            if (!seenNames.add(candidate.name)) return@forEach
            val signature = candidate.function.getSignature()
            val tailText = signature?.let { buildMacroTailText(it) } ?: "()"
            val typeText = signature?.returnType
            val lookupElement = LookupElementBuilder
                .create("${candidate.name}!")
                .withTailText(tailText)
                .withTypeText(typeText)
                .withInsertHandler { ctx, _ ->
                    val document = ctx.document
                    document.insertString(ctx.selectionEndOffset, "()")
                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                }
            result.addElement(PrioritizedLookupElement.withPriority(lookupElement, MACRO_PRIORITY))
        }

        val includeStdlib = path.parent is MvMacroCallExpr
        val macros = macroSemanticService.completionSpecs(includeStdlib)

        macros.forEach { macro ->
            if (!seenNames.add(macro.name)) return@forEach
            val lookupElement = LookupElementBuilder
                .create(macro.completionName)
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

    private data class UserMacroCandidate(
        val name: String,
        val function: MvFunction
    )

    private fun collectUserMacroCandidates(path: MvPath): List<UserMacroCandidate> {
        val ctx = ResolutionContext(path, isCompletion = true)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<UserMacroCandidate>()
        val processor = createProcessor { entry ->
            if (!entry.isVisibleFrom(path)) return@createProcessor
            val resolved = resolveAliases(entry.element) as? MvFunction ?: return@createProcessor
            if (!resolved.isMacro) return@createProcessor
            val name = entry.name
            if (!seen.add(name)) return@createProcessor
            result.add(UserMacroCandidate(name, resolved))
        }
        processNestedScopesUpwards(path, FUNCTIONS, ctx, processor)
        return result
    }

    private fun buildMacroTailText(signature: org.sui.ide.utils.FunctionSignature): String {
        val typeParamsText =
            if (signature.typeParameters.isNotEmpty()) {
                signature.typeParameters.joinToString(", ", "<", ">")
            } else {
                ""
            }
        val paramsText = signature.parameters.joinToString(", ", "(", ")")
        return typeParamsText + paramsText
    }

}
