package org.sui.ide.inspections.imports

import org.sui.cli.settings.debugError
import org.sui.ide.inspections.imports.UseItemType.*
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve2.PathKind
import org.sui.lang.core.resolve2.pathKind
import org.sui.lang.core.resolve2.util.forEachLeafSpeck
import org.sui.lang.core.resolve2.util.forEachUseFun

enum class UseItemType {
    MODULE, SELF_MODULE, ITEM;
}

data class UseItem(
    val useSpeck: MvUseSpeck,
    val nameOrAlias: String,
    val type: UseItemType,
    val scope: NamedItemScope
)

data class UseFunItem(
    val stmt: MvStmt,
    val aliasName: String,
    val function: MvFunction?,
    val scope: NamedItemScope,
)

val MvItemsOwner.useItems: List<UseItem> get() = this.useStmtList.flatMap { it.useItems }

val MvItemsOwner.useFunItems: List<UseFunItem>
    get() = buildList {
        this@useFunItems.useFunStmtList.forEach { addAll(it.useFunItems) }
        this@useFunItems.publicUseFunStmtList.forEach { addAll(it.useFunItems) }
    }

val MvUseStmt.useItems: List<UseItem>
    get() {
        val items = mutableListOf<UseItem>()
        val stmtItemScope = this.usageScope
        this.forEachLeafSpeck { speckPath, useAlias ->
            val useSpeck = speckPath.parent as MvUseSpeck
            val nameOrAlias = useAlias?.name ?: speckPath.referenceName ?: return@forEachLeafSpeck false
            val pathKind = speckPath.pathKind()
            when (pathKind) {
                is PathKind.QualifiedPath.Module ->
                    items.add(UseItem(useSpeck, nameOrAlias, MODULE, stmtItemScope))

                is PathKind.QualifiedPath.ModuleItem -> {
                    debugError("not reachable, must be a bug")
                    return@forEachLeafSpeck false
                }

                is PathKind.QualifiedPath -> {
                    if (pathKind.path.referenceName == "Self") {
                        val moduleName =
                            useAlias?.name ?: pathKind.qualifier.referenceName ?: return@forEachLeafSpeck false
                        items.add(UseItem(useSpeck, moduleName, SELF_MODULE, stmtItemScope))
                    } else {
                        items.add(UseItem(useSpeck, nameOrAlias, ITEM, stmtItemScope))
                    }
                }

                else -> return@forEachLeafSpeck false
            }
            false
        }

        return items
    }

val MvUseFunStmt.useFunItems: List<UseFunItem>
    get() {
        var item: UseFunItem? = null
        this.forEachUseFun { path, alias ->
            item = createUseFunItem(this, path, alias)
            false
        }
        return listOfNotNull(item)
    }

val MvPublicUseFunStmt.useFunItems: List<UseFunItem>
    get() {
        var item: UseFunItem? = null
        this.forEachUseFun { path, alias ->
            item = createUseFunItem(this, path, alias)
            false
        }
        return listOfNotNull(item)
    }

private fun createUseFunItem(
    stmt: MvStmt,
    path: MvPath,
    alias: MvUseFunAlias?,
): UseFunItem? {
    val aliasName = alias?.name ?: return null
    val function = path.reference?.resolveFollowingAliases() as? MvFunction
    return UseFunItem(stmt, aliasName, function, stmt.usageScope)
}
