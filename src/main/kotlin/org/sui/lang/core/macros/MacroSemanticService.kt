package org.sui.lang.core.macros

import org.sui.lang.core.psi.MvMacroCallExpr
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.psi.ext.MvStructOrEnumItemElement
import org.sui.lang.core.psi.typeParameters
import org.sui.lang.core.psi.psiFactory
import org.sui.lang.core.resolve2.PreImportedModuleService
import org.sui.lang.core.types.infer.Substitution
import org.sui.lang.core.types.infer.TypeInferenceWalker
import org.sui.lang.core.types.ty.*

interface MacroSemanticService {
    fun specOf(name: String): MacroSpec?
    fun isBuiltin(name: String): Boolean
    fun completionSpecs(includeStdlib: Boolean): List<MacroSpec>
    fun expectedArgsCount(call: MvMacroCallExpr): Int?
    fun inferReturnType(call: MvMacroCallExpr, inference: TypeInferenceWalker): Ty?
}

object DefaultMacroSemanticService : MacroSemanticService {
    private val alwaysVisibleStdlibMacros = setOf("assert_eq", "assert_ref_eq")

    override fun specOf(name: String): MacroSpec? = MvMacroRegistry.specOf(name)

    override fun isBuiltin(name: String): Boolean = MvMacroRegistry.isBuiltin(name)

    override fun completionSpecs(includeStdlib: Boolean): List<MacroSpec> {
        val specs = MvMacroRegistry.completionSpecs()
        if (includeStdlib) return specs
        return specs.filter { isBuiltin(it.name) || it.name in alwaysVisibleStdlibMacros }
    }

    override fun expectedArgsCount(call: MvMacroCallExpr): Int? {
        val name = call.path.referenceName ?: return null
        return specOf(name)?.fixedArgsCountOrNull()
    }

    override fun inferReturnType(call: MvMacroCallExpr, inference: TypeInferenceWalker): Ty? {
        val name = call.path.referenceName ?: return null
        val builtinSpec = MvMacroRegistry.builtinSpecOf(name) ?: return null
        return when (builtinSpec.returnKind) {
            MacroReturnKind.UNIT -> {
                inferAllArgumentTypes(call, inference)
                TyUnit
            }
            MacroReturnKind.UNKNOWN -> {
                inferAllArgumentTypes(call, inference)
                TyUnknown
            }
            MacroReturnKind.BYTE_STRING -> inferByteStringReturnType(call, inference)
            MacroReturnKind.OPTION -> inferOptionReturnType(call, inference)
            MacroReturnKind.RESULT -> inferResultReturnType(call, inference)
        }
    }

    private fun inferAllArgumentTypes(call: MvMacroCallExpr, inference: TypeInferenceWalker) {
        call.valueArguments.forEach { it.expr?.let(inference::inferType) }
    }

    private fun inferByteStringReturnType(call: MvMacroCallExpr, inference: TypeInferenceWalker): Ty {
        if (call.valueArguments.size == 1) {
            call.valueArguments.first().expr?.let(inference::inferType)
        }
        return TyByteString(inference.msl)
    }

    private fun inferOptionReturnType(call: MvMacroCallExpr, inference: TypeInferenceWalker): Ty {
        if (call.valueArguments.size != 1) return TyUnknown
        val argTy = call.valueArguments.first().expr?.let(inference::inferType) ?: TyUnknown
        if (argTy == TyUnknown) return TyUnknown

        return try {
            val optionType = resolveStdlibStruct(
                inference,
                moduleName = "option",
                structName = "Option",
                dummyDefinition =
                """
                struct Option<T> has copy, drop {
                    vec: vector<u8>
                }
                """.trimIndent()
            ) ?: return TyUnknown

            instantiateAdt(optionType, listOf(argTy))
        } catch (_: Exception) {
            TyUnknown
        }
    }

    private fun inferResultReturnType(call: MvMacroCallExpr, inference: TypeInferenceWalker): Ty {
        if (call.valueArguments.size != 2) return TyUnknown
        val okTy = call.valueArguments[0].expr?.let(inference::inferType) ?: TyUnknown
        val errTy = call.valueArguments[1].expr?.let(inference::inferType) ?: TyUnknown
        if (okTy == TyUnknown || errTy == TyUnknown) return TyUnknown

        return try {
            val resultType = resolveStdlibStruct(
                inference,
                moduleName = "result",
                structName = "Result",
                dummyDefinition =
                """
                struct Result<T, E> has copy, drop {
                    vec: vector<u8>
                }
                """.trimIndent()
            ) ?: return TyUnknown

            instantiateAdt(resultType, listOf(okTy, errTy))
        } catch (_: Exception) {
            TyUnknown
        }
    }

    private fun instantiateAdt(item: MvStructOrEnumItemElement, typeArgs: List<Ty>): TyAdt {
        val substitution = Substitution(
            item.typeParameters.withIndex().associate { (index, param) ->
                TyTypeParameter(param) to typeArgs.getOrElse(index) { TyUnknown }
            }
        )
        return TyAdt(item, substitution, typeArgs)
    }

    private fun resolveStdlibStruct(
        inference: TypeInferenceWalker,
        moduleName: String,
        structName: String,
        dummyDefinition: String,
    ): MvStructOrEnumItemElement? {
        val service = PreImportedModuleService.getInstance(inference.project)
        val preImportedModules = service.getPreImportedModules()
        val module = preImportedModules.find { it.name == moduleName }
        val resolvedStruct = module?.structs()?.firstOrNull { it.name == structName }
        if (resolvedStruct != null) return resolvedStruct

        val dummyModule = inference.project.psiFactory.inlineModule("std", moduleName, dummyDefinition)
        return dummyModule.structs().firstOrNull { it.name == structName }
    }
}
