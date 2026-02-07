package org.sui.lang.core.psi.ext

import com.intellij.psi.PsiComment
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.stubChildrenOfType
import org.sui.stdext.buildList

interface MvItemsOwner: MvElement {
    val useStmtList: List<MvUseStmt>
        get() = this.stubChildrenOfType()

    val useFunStmtList: List<MvUseFunStmt>
        get() = this.childrenOfType()

    val publicUseFunStmtList: List<MvPublicUseFunStmt>
        get() = this.childrenOfType()
}

fun MvItemsOwner.items(): Sequence<MvElement> {
    val startChild = when (this) {
        is MvModule -> this.firstChild
        is MvScript -> this.firstChild
        else -> this.firstChild
    }
    return generateSequence(startChild) { it.nextSibling }
        .filterIsInstance<MvElement>()
//        .filter { it !is MvAttr }
}

val MvItemsOwner.itemElements: List<MvItemElement>
    get() {
        return this.items().filterIsInstance<MvItemElement>().toList()
    }

val MvModule.innerSpecItems: List<MvItemElement>
    get() {
        val module = this
        return buildList {
            addAll(module.allModuleSpecs()
                       .map {
                           it.moduleItemSpecs()
                               .flatMap { spec -> spec.itemSpecBlock?.globalVariables().orEmpty() }
                       }
                       .flatten())
            addAll(module.specInlineFunctions())
        }
    }

val MvItemsOwner.firstItem: MvElement?
    get() {
        return when (this) {
            is MvModule -> {
                // For modules, find the first valid item (excluding use statements and attributes)
                // This ensures import optimizer inserts use statements before actual module items
                items().firstOrNull {
                    when (it) {
                        // Skip attributes and use statements
                        is MvAttr -> false
                        is MvUseStmt -> false
                        is MvUseFunStmt -> false
                        is MvPublicUseFunStmt -> false
                        // Valid items: module items (functions, structs, consts, enums)
                        is MvFunction, is MvStruct, is MvConst, is MvEnum, is MvTypeAlias -> true
                        // Skip other elements
                        else -> false
                    }
                }
            }
            else -> {
                // For other types of itemsOwner, keep the original behavior
                items().firstOrNull { it !is MvAttr }
            }
        }
    }
