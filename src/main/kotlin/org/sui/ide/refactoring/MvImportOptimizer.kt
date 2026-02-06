package org.sui.ide.refactoring

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import org.sui.ide.inspections.MvUnusedImportInspection
import org.sui.ide.inspections.imports.ImportAnalyzer2
import org.sui.ide.utils.imports.COMPARATOR_FOR_ITEMS_IN_USE_GROUP
import org.sui.ide.utils.imports.UseStmtWrapper
import org.sui.lang.MoveFile
import org.sui.lang.core.psi.MvModule
import org.sui.lang.core.psi.MvPsiFactory
import org.sui.lang.core.psi.MvPublicUseFunStmt
import org.sui.lang.core.psi.MvStmt
import org.sui.lang.core.psi.MvUseFunStmt
import org.sui.lang.core.psi.MvUseGroup
import org.sui.lang.core.psi.MvUseSpeck
import org.sui.lang.core.psi.MvUseStmt
import org.sui.lang.core.psi.deleteWithSurroundingCommaAndWhitespace
import org.sui.lang.core.psi.ext.MvItemsOwner
import org.sui.lang.core.psi.ext.MvDocAndAttributeOwner
import org.sui.lang.core.psi.ext.asTrivial
import org.sui.lang.core.psi.ext.descendantsOfType
import org.sui.lang.core.psi.ext.firstItem
import org.sui.lang.core.psi.ext.getNextNonCommentSibling
import org.sui.lang.core.psi.ext.hasTestOnlyAttr
import org.sui.lang.core.psi.ext.hasVerifyOnlyAttr
import org.sui.lang.core.psi.ext.rightSiblings
import org.sui.lang.core.psi.psiFactory
import org.sui.stdext.withNext

class MvImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile) = file is MoveFile

    override fun processFile(file: PsiFile) = Runnable {
        if (!MvUnusedImportInspection.isEnabled(file.project)) return@Runnable

        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)
        if (document != null) {
            documentManager.commitDocument(document)
        }

        val holder = ProblemsHolder(InspectionManager.getInstance(file.project), file, false)
        val importVisitor = ImportAnalyzer2(holder)
        object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is MvItemsOwner) {
                    importVisitor.analyzeImportsOwner(element)
                } else {
                    super.visitElement(element)
                }
            }
        }.visitFile(file)

        val useElements = holder.results.map { it.psiElement }
        for (useElement in useElements) {
            when (useElement) {
                is MvUseStmt -> {
                    (useElement.nextSibling as? PsiWhiteSpace)?.delete()
                    useElement.delete()
                }

                is MvUseSpeck -> {
                    // remove whitespace following comma, if first position in a group
                    val useGroup = useElement.parent as? MvUseGroup
                    if (useGroup != null
                        && useGroup.useSpeckList.firstOrNull() == useElement
                    ) {
                        val followingComma = useElement.getNextNonCommentSibling()
                        followingComma?.rightSiblings
                            ?.takeWhile { it is PsiWhiteSpace }
                            ?.forEach { it.delete() }
                    }
                    useElement.deleteWithSurroundingCommaAndWhitespace()
                }

                is MvUseFunStmt -> {
                    (useElement.nextSibling as? PsiWhiteSpace)?.delete()
                    useElement.delete()
                }

                is MvPublicUseFunStmt -> {
                    (useElement.nextSibling as? PsiWhiteSpace)?.delete()
                    useElement.delete()
                }
            }
        }

        val psiFactory = file.project.psiFactory

        val useStmtsWithOwner = file.descendantsOfType<MvUseStmt>().groupBy { it.parent }
        for ((stmtOwner, useStmts) in useStmtsWithOwner.entries) {
            useStmts.forEach { useStmt ->
                useStmt.useSpeck?.let {
                    removeCurlyBracesIfPossible(it, psiFactory)
                    it.useGroup?.sortUseSpecks()
                }
            }
            if (stmtOwner is MvModule) {
                reorderUseStmtsIntoGroups(stmtOwner)
            }
        }

        val useFunStmtsWithOwner =
            (file.descendantsOfType<MvUseFunStmt>() + file.descendantsOfType<MvPublicUseFunStmt>())
                .groupBy { it.parent }
        for ((stmtOwner, _) in useFunStmtsWithOwner.entries) {
            if (stmtOwner is MvItemsOwner) {
                reorderUseFunStmtsIntoGroups(stmtOwner)
            }
        }
    }

    /** Returns true if successfully removed, e.g. `use aaa::{bbb};` -> `use aaa::bbb;` */
    private fun removeCurlyBracesIfPossible(rootUseSpeck: MvUseSpeck, psiFactory: MvPsiFactory) {
        val itemUseSpeck = rootUseSpeck.useGroup?.asTrivial ?: return
        val newUseSpeck = psiFactory.useSpeck("0x1::dummy::call")
        val newUseSpeckPath = newUseSpeck.path
        newUseSpeckPath.path?.replace(rootUseSpeck.path)
        itemUseSpeck.path.identifier?.let { newUseSpeckPath.identifier?.replace(it) }

        val useAlias = itemUseSpeck.useAlias
        if (useAlias != null) {
            newUseSpeck.add(useAlias)
        }

        rootUseSpeck.replace(newUseSpeck)
    }

    private fun reorderUseStmtsIntoGroups(itemsOwner: MvItemsOwner) {
        val useStmts = itemsOwner.useStmtList
        val firstItem = itemsOwner.firstItem ?: return
        val psiFactory = itemsOwner.project.psiFactory
        val sortedUses = useStmts
            .asSequence()
            .map { UseStmtWrapper(it) }
            .sorted()
        for ((useWrapper, nextUseWrapper) in sortedUses.withNext()) {
            val addedUseItem = itemsOwner.addBefore(useWrapper.useStmt, firstItem)
            itemsOwner.addAfter(psiFactory.createNewline(), addedUseItem)
            val addNewLine =
                useWrapper.packageGroupLevel != nextUseWrapper?.packageGroupLevel
            if (addNewLine) {
                itemsOwner.addAfter(psiFactory.createNewline(), addedUseItem)
            }
        }
        useStmts.forEach {
            (it.nextSibling as? PsiWhiteSpace)?.delete()
            it.delete()
        }
    }

    private fun reorderUseFunStmtsIntoGroups(itemsOwner: MvItemsOwner) {
        val useFunStmts = buildList<MvStmt> {
            addAll(itemsOwner.useFunStmtList)
            addAll(itemsOwner.publicUseFunStmtList)
        }
        if (useFunStmts.isEmpty()) return

        val firstItem = itemsOwner.firstItem ?: return
        val psiFactory = itemsOwner.project.psiFactory
        val sortedUses = useFunStmts
            .asSequence()
            .map { UseFunStmtWrapper(it) }
            .sorted()
        for ((useWrapper, nextUseWrapper) in sortedUses.withNext()) {
            val addedUseItem = itemsOwner.addBefore(useWrapper.useStmt, firstItem)
            itemsOwner.addAfter(psiFactory.createNewline(), addedUseItem)
            val addNewLine = useWrapper.groupLevel != nextUseWrapper?.groupLevel
            if (addNewLine) {
                itemsOwner.addAfter(psiFactory.createNewline(), addedUseItem)
            }
        }
        useFunStmts.forEach {
            (it.nextSibling as? PsiWhiteSpace)?.delete()
            it.delete()
        }
    }

    private fun MvUseGroup.sortUseSpecks() {
        val sortedList = useSpeckList
            .sortedWith(COMPARATOR_FOR_ITEMS_IN_USE_GROUP)
            .map { it.copy() }
        useSpeckList.zip(sortedList).forEach { it.first.replace(it.second) }
    }
}

private data class UseFunStmtWrapper(val useStmt: MvStmt) : Comparable<UseFunStmtWrapper> {
    private val attrOwner: MvDocAndAttributeOwner? = useStmt as? MvDocAndAttributeOwner

    val groupLevel: Int = when {
        attrOwner?.hasTestOnlyAttr == true -> 2
        attrOwner?.hasVerifyOnlyAttr == true -> 3
        useStmt is MvUseFunStmt -> 0
        useStmt is MvPublicUseFunStmt -> 1
        else -> 4
    }

    override fun compareTo(other: UseFunStmtWrapper): Int =
        compareValuesBy(
            this,
            other,
            { it.groupLevel },
            { it.useStmt.text.lowercase() }
        )
}
