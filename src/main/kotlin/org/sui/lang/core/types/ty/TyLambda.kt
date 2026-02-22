package org.sui.lang.core.types.ty

import org.sui.ide.presentation.tyToString
import org.sui.lang.core.types.infer.TypeFolder
import org.sui.lang.core.types.infer.TypeVisitor
import org.sui.lang.core.types.infer.mergeFlags

// TyCallable is not a GenericTy: lambdas are anonymous type expressions without their own
// type parameters or PSI declarations, unlike TyFunction which wraps a named MvFunctionLike.
interface TyCallable {
    val paramTypes: List<Ty>
    val retType: Ty
}

data class TyLambda(
    override val paramTypes: List<Ty>,
    override val retType: Ty
) : Ty(mergeFlags(paramTypes) or retType.flags), TyCallable {

    override fun abilities(): Set<Ability> = emptySet()

    override fun toString(): String = tyToString(this)

    override fun innerFoldWith(folder: TypeFolder): Ty {
        return TyLambda(
            paramTypes.map { it.foldWith(folder) },
            retType.foldWith(folder),
        )
    }

    override fun innerVisitWith(visitor: TypeVisitor): Boolean =
        paramTypes.any { it.visitWith(visitor) } || retType.visitWith(visitor)

    companion object {
        fun unknown(numParams: Int): TyLambda {
            return TyLambda(generateSequence { TyUnknown }.take(numParams).toList(), TyUnknown)
        }
    }
}
