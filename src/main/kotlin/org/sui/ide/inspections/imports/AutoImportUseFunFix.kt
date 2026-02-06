package org.sui.ide.inspections.imports

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.ide.DataManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.sui.ide.inspections.DiagnosticFix
import org.sui.ide.utils.imports.ImportCandidate
import org.sui.ide.utils.imports.ImportCandidateCollector
import org.sui.ide.utils.imports.importAsUseFun
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvMethodCall
import org.sui.lang.core.psi.ext.inferReceiverTy
import org.sui.lang.core.psi.ext.isMsl
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve2.util.isMethodCompatibleWithReceiver
import org.sui.lang.core.types.ty.TyUnknown
import org.sui.lang.index.MvNamedElementIndex
import org.sui.openapiext.runWriteCommandAction

class AutoImportUseFunFix(element: MvMethodCall) : DiagnosticFix<MvMethodCall>(element),
                                                   HighPriorityAction {

    private var isConsumed: Boolean = false

    override fun getFamilyName(): String = NAME

    override fun getText(): String = familyName

    override fun stillApplicable(project: Project, file: PsiFile, element: MvMethodCall): Boolean =
        !isConsumed

    override fun invoke(project: Project, file: PsiFile, element: MvMethodCall) {
        val context = findApplicableContext(element) ?: return
        val candidates = context.candidates
        if (candidates.isEmpty()) return

        if (candidates.size == 1) {
            project.runWriteCommandAction {
                candidates.first().importAsUseFun(element)
            }
        } else {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
                chooseItemAndImport(project, it, candidates, element)
            }
        }

        isConsumed = true
    }

    private fun chooseItemAndImport(
        project: Project,
        dataContext: DataContext,
        items: List<ImportCandidate>,
        context: MvMethodCall,
    ) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            project.runWriteCommandAction {
                selectedValue.importAsUseFun(context)
            }
        }
    }

    data class Context(val candidates: List<ImportCandidate>)

    companion object {
        const val NAME: String = AutoImportFix.NAME

        fun findApplicableContext(methodCall: MvMethodCall): Context? {
            val methodRef = methodCall.reference ?: return null
            val resolvedVariants = methodRef.multiResolve()
            when {
                resolvedVariants.size == 1 -> return null
                resolvedVariants.size > 1 -> return null
            }

            val methodName = methodCall.referenceName ?: return null
            val msl = methodCall.isMsl()
            val receiverTy = methodCall.inferReceiverTy(msl)
            if (receiverTy is TyUnknown) return null

            val importContext = ImportContext.from(methodCall, setOf(Namespace.FUNCTION)) ?: return null
            val candidates = collectCandidates(importContext, methodName)
                .filter { candidate ->
                    val function = candidate.element as? MvFunction ?: return@filter false
                    function.isMethodCompatibleWithReceiver(receiverTy, msl)
                }
                .distinctBy { it.qualName.editorText() }
            if (candidates.isEmpty()) return null

            return Context(candidates)
        }

        private fun collectCandidates(importContext: ImportContext, methodName: String): List<ImportCandidate> {
            val exactNameCandidates = ImportCandidateCollector.getImportCandidates(importContext, methodName)
            if (exactNameCandidates.isNotEmpty()) return exactNameCandidates

            val allNames = MvNamedElementIndex.getAllKeys(importContext.contextElement.project)
            return allNames
                .asSequence()
                .flatMap { candidateName ->
                    ProgressManager.checkCanceled()
                    ImportCandidateCollector.getImportCandidates(importContext, candidateName).asSequence()
                }
                .distinctBy { it.element }
                .toList()
        }
    }
}
