package org.sui.ide.inspections.imports

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.sui.ide.inspections.imports.UseItemType.*
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve2.util.isMethodCompatibleWithReceiver
import org.sui.lang.core.types.ty.TyUnknown
import org.sui.stdext.chain

class ImportAnalyzer2(val holder: ProblemsHolder) : MvVisitor() {

    override fun visitModule(o: MvModule) = analyzeImportsOwner(o)
    override fun visitScript(o: MvScript) = analyzeImportsOwner(o)
    override fun visitModuleSpecBlock(o: MvModuleSpecBlock) = analyzeImportsOwner(o)

    fun analyzeImportsOwner(importsOwner: MvItemsOwner) {
        analyzeUseStmtsForScope(importsOwner, NamedItemScope.TEST)
        analyzeUseStmtsForScope(importsOwner, NamedItemScope.MAIN)
    }

    private fun analyzeUseStmtsForScope(rootItemsOwner: MvItemsOwner, itemScope: NamedItemScope) {
        val allUseItemsHit = mutableSetOf<UseItem>()
        val allUseFunItemsHit = mutableSetOf<UseFunItem>()
        val rootItemOwnerWithSiblings = rootItemsOwner.itemsOwnerWithSiblings

        val allFiles = rootItemOwnerWithSiblings.mapNotNull { it.containingMoveFile }.distinct()
        val fileUseItems = allFiles
            // collect every possible MvItemOwner
            .flatMap { it.descendantsOfType<MvItemsOwner>().flatMap { i -> i.itemsOwnerWithSiblings } }
            .distinct()
            .associateWith { itemOwner ->
                itemOwner.useItems.filter { it.scope == itemScope }
            }
        val fileUseFunItems = allFiles
            // collect every possible MvItemOwner
            .flatMap { it.descendantsOfType<MvItemsOwner>().flatMap { i -> i.itemsOwnerWithSiblings } }
            .distinct()
            .associateWith { itemOwner ->
                itemOwner.useFunItems.filter { it.scope == itemScope }
            }

        val reachablePaths =
            rootItemOwnerWithSiblings
                .flatMap { it.descendantsOfType<MvPath>() }
                .filter { it.basePath() == it }
                .filter { it.usageScope == itemScope }
                .filter { !it.hasAncestor<MvUseSpeck>() }

        for (path in reachablePaths) {
            val basePathType = path.basePathType()
            for (itemsOwner in path.ancestorsOfType<MvItemsOwner>()) {
                val reachableUseItems =
                    itemsOwner.itemsOwnerWithSiblings.flatMap { fileUseItems[it]!! }
                val useItemHit =
                    when (basePathType) {
                        is BasePathType.Item -> {
                            reachableUseItems.filter { it.type == ITEM }
                                // only hit first encountered to remove duplicates
                                .firstOrNull { it.nameOrAlias == basePathType.itemName }
                        }

                        is BasePathType.Module -> {
                            reachableUseItems.filter { it.type == MODULE || it.type == SELF_MODULE }
                                // only hit first encountered to remove duplicates
                                .firstOrNull { it.nameOrAlias == basePathType.moduleName }
                        }
                        // BasePathType.Address is fq path, and doesn't participate in imports
                        else -> null
                    }
                if (useItemHit != null) {
                    allUseItemsHit.add(useItemHit)
                    break
                }
            }
        }

        val reachableMethodCalls =
            rootItemOwnerWithSiblings
                .flatMap { it.descendantsOfType<MvMethodCall>() }
                .filter { it.usageScope == itemScope }

        for (methodCall in reachableMethodCalls) {
            val methodName = methodCall.referenceName ?: continue
            val msl = methodCall.isMsl()
            val receiverTy = methodCall.inferReceiverTy(msl)
            for (itemsOwner in methodCall.ancestorsOfType<MvItemsOwner>()) {
                val reachableUseFunItems =
                    itemsOwner.itemsOwnerWithSiblings.flatMap { fileUseFunItems[it]!! }
                val useFunItemHit =
                    reachableUseFunItems.firstOrNull {
                        if (it.aliasName != methodName) return@firstOrNull false
                        if (receiverTy is TyUnknown || it.function == null) return@firstOrNull true
                        it.function.isMethodCompatibleWithReceiver(receiverTy, msl)
                    }
                if (useFunItemHit != null) {
                    allUseFunItemsHit.add(useFunItemHit)
                    break
                }
            }
        }

        // includes self
        val reachableItemsOwners = rootItemsOwner.descendantsOfTypeOrSelf<MvItemsOwner>()
        for (itemsOwner in reachableItemsOwners) {
            val scopeUseStmts = itemsOwner.useStmtList.filter { it.usageScope == itemScope }
            for (useStmt in scopeUseStmts) {
                val unusedUseItems = useStmt.useItems.toSet() - allUseItemsHit
                holder.registerStmtSpeckError2(useStmt, unusedUseItems)
            }

            val scopeUseFunStmts = itemsOwner.useFunStmtList.filter { it.usageScope == itemScope }
            for (useFunStmt in scopeUseFunStmts) {
                val unusedUseFunItems = useFunStmt.useFunItems.toSet() - allUseFunItemsHit
                holder.registerUseFunStmtError(useFunStmt, unusedUseFunItems)
            }

            val scopePublicUseFunStmts = itemsOwner.publicUseFunStmtList.filter { it.usageScope == itemScope }
            for (useFunStmt in scopePublicUseFunStmts) {
                val unusedUseFunItems = useFunStmt.useFunItems.toSet() - allUseFunItemsHit
                holder.registerUseFunStmtError(useFunStmt, unusedUseFunItems)
            }
        }
    }
}

fun ProblemsHolder.registerStmtSpeckError2(useStmt: MvUseStmt, useItems: Set<UseItem>) {
    val moduleUseItems = useItems.filter { it.type == MODULE }
    if (moduleUseItems.isNotEmpty()) {
        this.registerProblem(
            useStmt,
            "Unused use item",
            ProblemHighlightType.LIKE_UNUSED_SYMBOL
        )
        return
    }

    if (useStmt.useItems.size == useItems.size) {
        // all inner speck types are covered, highlight complete useStmt
        this.registerProblem(
            useStmt,
            "Unused use item",
            ProblemHighlightType.LIKE_UNUSED_SYMBOL
        )
    } else {
        for (useItem in useItems) {
            this.registerProblem(
                useItem.useSpeck,
                "Unused use item",
                ProblemHighlightType.LIKE_UNUSED_SYMBOL
            )
        }
    }
}

fun ProblemsHolder.registerUseFunStmtError(useFunStmt: MvStmt, useFunItems: Set<UseFunItem>) {
    if (useFunItems.isEmpty()) return

    this.registerProblem(
        useFunStmt,
        "Unused use item",
        ProblemHighlightType.LIKE_UNUSED_SYMBOL
    )
}

val MvItemsOwner.itemsOwnerWithSiblings: List<MvItemsOwner>
    get() {
        return when (this) {
            is MvModule -> {
                // add all module spec blocks
                listOf(this).chain(this.allModuleSpecBlocks()).toList()
            }

            is MvModuleSpecBlock -> {
                // add module block
                val moduleItem = this.moduleSpec.moduleItem
                if (moduleItem != null) {
                    listOf(moduleItem, this)
                } else {
                    listOf(this)
                }
            }

            else -> listOf(this)
        }
    }
