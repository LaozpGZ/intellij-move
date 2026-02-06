package org.sui.lang.core.resolve2.util

import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.MvPublicUseFunStmt
import org.sui.lang.core.psi.MvUseFunAlias
import org.sui.lang.core.psi.MvUseFunStmt
import org.sui.lang.core.psi.ext.aliasOrNull
import org.sui.lang.core.psi.ext.pathOrNull
import org.sui.lang.core.psi.ext.publicUseFunItem
import org.sui.lang.core.psi.ext.useFunItem
import org.sui.lang.core.psi.selfParamTy
import org.sui.lang.core.types.infer.deepFoldTyTypeParameterWith
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.core.types.ty.TyInfer
import org.sui.lang.core.types.ty.TyReference

fun interface UseFunConsumer {
    fun consume(path: MvPath, alias: MvUseFunAlias?): Boolean
}

fun MvUseFunStmt.forEachUseFun(consumer: UseFunConsumer) {
    val useFun = this.useFunItem
    val path = useFun.pathOrNull ?: return
    consumer.consume(path, useFun.aliasOrNull)
}

fun MvPublicUseFunStmt.forEachUseFun(consumer: UseFunConsumer) {
    val useFun = this.publicUseFunItem
    val path = useFun.pathOrNull ?: return
    consumer.consume(path, useFun.aliasOrNull)
}

fun MvFunction.isMethodCompatibleWithReceiver(receiverTy: Ty, msl: Boolean): Boolean {
    val selfTy = this.selfParamTy(msl) ?: return false
    val selfTyWithTyVars =
        selfTy.deepFoldTyTypeParameterWith { typeParameter -> TyInfer.TyVar(typeParameter) }
    return TyReference.isCompatibleWithAutoborrow(receiverTy, selfTyWithTyVars, msl)
}
