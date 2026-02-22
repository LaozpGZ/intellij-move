package org.sui.lang.core.resolve2

import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.*
import org.sui.lang.core.resolve.ref.Namespace
import org.sui.lang.core.resolve2.util.forEachUseFun
import org.sui.lang.core.resolve2.util.isMethodCompatibleWithReceiver
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.moveProject
import java.util.*

val MvNamedElement.namespace
    get() = when (this) {
        is MvFunctionLike -> Namespace.FUNCTION
        is MvStruct -> Namespace.TYPE
        is MvTypeAlias -> Namespace.TYPE
        is MvEnum -> Namespace.ENUM
        is MvConst -> Namespace.NAME
        is MvSchema -> Namespace.SCHEMA
        is MvModule -> Namespace.MODULE
        is MvGlobalVariableStmt -> Namespace.NAME
        else -> error("when should be exhaustive, $this is not covered")
    }

fun processMethodResolveVariants(
    methodOrField: MvMethodOrField,
    receiverTy: Ty,
    msl: Boolean,
    processor: RsResolveProcessor
): Boolean {
    if (processUseFunMethodResolveVariants(methodOrField, receiverTy, msl, processor)) {
        return true
    }

    val moveProject = methodOrField.moveProject ?: return false
    val itemModule = receiverTy.itemModule(moveProject) ?: return false
    return processor
        .wrapWithFilter { entry ->
            val function = entry.element as? MvFunction ?: return@wrapWithFilter false
            function.isMethodCompatibleWithReceiver(receiverTy, msl)
        }
        .processAllItems(setOf(Namespace.FUNCTION), itemModule.allNonTestFunctions())
}

private fun processUseFunMethodResolveVariants(
    methodOrField: MvMethodOrField,
    receiverTy: Ty,
    msl: Boolean,
    processor: RsResolveProcessor
): Boolean {
    val baseProcessor = processor.wrapWithFilter { entry ->
        val function = entry.element as? MvFunction ?: return@wrapWithFilter false
        function.isMethodCompatibleWithReceiver(receiverTy, msl)
    }

    // Step 1: Walk up the current scope chain for local use fun / public use fun declarations
    var currentModule: MvModule? = null
    var scope: MvElement? = methodOrField
    while (scope != null) {
        val itemsOwner = scope as? MvItemsOwner
        if (itemsOwner != null) {
            for (useFunStmt in itemsOwner.useFunStmtList) {
                if (processUseFunStmt(useFunStmt, baseProcessor)) {
                    return true
                }
            }
            for (publicUseFunStmt in itemsOwner.publicUseFunStmtList) {
                if (processPublicUseFunStmt(publicUseFunStmt, baseProcessor)) {
                    return true
                }
            }
        }
        if (scope is MvModule) {
            currentModule = scope
            break
        }
        scope = scope.context as? MvElement
    }

    // Step 2: Check public use fun declarations in the receiver type's defining module.
    // Per Move 2024 spec, `public use fun` aliases declared in the type's defining module
    // are visible as methods in all modules.
    val moveProject = methodOrField.moveProject ?: return false
    val definingModule = receiverTy.itemModule(moveProject) ?: return false
    if (definingModule != currentModule) {
        for (publicUseFunStmt in definingModule.publicUseFunStmtList) {
            if (processPublicUseFunStmt(publicUseFunStmt, baseProcessor)) {
                return true
            }
        }
    }

    return false
}

private fun processUseFunStmt(
    useFunStmt: MvUseFunStmt,
    processor: RsResolveProcessor
): Boolean {
    var matched = false
    useFunStmt.forEachUseFun { path, alias ->
        val function = path.reference?.resolveFollowingAliases() as? MvFunction ?: return@forEachUseFun false
        val aliasName = alias?.name ?: return@forEachUseFun false
        if (processor.process(aliasName, function, setOf(Namespace.FUNCTION))) {
            matched = true
            return@forEachUseFun true
        }
        false
    }
    return matched
}

private fun processPublicUseFunStmt(
    publicUseFunStmt: MvPublicUseFunStmt,
    processor: RsResolveProcessor
): Boolean {
    var matched = false
    publicUseFunStmt.forEachUseFun { path, alias ->
        val function = path.reference?.resolveFollowingAliases() as? MvFunction ?: return@forEachUseFun false
        val aliasName = alias?.name ?: return@forEachUseFun false
        if (processor.process(aliasName, function, setOf(Namespace.FUNCTION))) {
            matched = true
            return@forEachUseFun true
        }
        false
    }
    return matched
}

fun processItemDeclarations(
    itemsOwner: MvItemsOwner,
    ns: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean {

    // 1. loop over all items in module (item is anything accessible with MODULE:: )
    // 2. for every item, use it's .visibility to create VisibilityFilter, even it's just a { false }
    val items = itemsOwner.itemElements +
            (itemsOwner as? MvModule)?.innerSpecItems.orEmpty() +
            (itemsOwner as? MvModule)?.let { getItemsFromModuleSpecs(it, ns) }.orEmpty()

    for (item in items) {
        val name = item.name ?: continue
        val namespace = item.namespace
        if (namespace !in ns) continue

        if (processor.process(name, item, setOf(namespace))) return true
    }

    return false
}

fun getItemsFromModuleSpecs(module: MvModule, ns: Set<Namespace>): List<MvItemElement> {
    val c = mutableListOf<MvItemElement>()
    processItemsFromModuleSpecs(module, ns, createProcessor { c.add(it.element as MvItemElement) })
    return c
}

fun processItemsFromModuleSpecs(
    module: MvModule,
    namespaces: Set<Namespace>,
    processor: RsResolveProcessor,
): Boolean {
    for (namespace in namespaces) {
        val thisNs = setOf(namespace)
        for (moduleSpec in module.allModuleSpecs()) {
            val matched = when (namespace) {
                Namespace.FUNCTION ->
                    processor.processAll(
                        thisNs,
                        moduleSpec.specFunctions(),
                        moduleSpec.specInlineFunctions(),
                    )
                Namespace.SCHEMA -> processor.processAll(thisNs, moduleSpec.schemas())
                else -> false
            }
            if (matched) return true
        }
    }
    return false
}
