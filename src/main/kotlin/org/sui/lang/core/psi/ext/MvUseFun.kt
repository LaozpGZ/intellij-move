package org.sui.lang.core.psi.ext

import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.MvPublicUseFun
import org.sui.lang.core.psi.MvPublicUseFunStmt
import org.sui.lang.core.psi.MvUseFun
import org.sui.lang.core.psi.MvUseFunAlias
import org.sui.lang.core.psi.MvUseFunStmt

val MvUseFunStmt.useFunItem: MvUseFun get() = this.useFun

val MvPublicUseFunStmt.publicUseFunItem: MvPublicUseFun get() = this.publicUseFun

val MvUseFun.pathOrNull: MvPath? get() = this.path

val MvPublicUseFun.pathOrNull: MvPath? get() = this.path

val MvUseFun.aliasOrNull: MvUseFunAlias? get() = this.useFunAlias

val MvPublicUseFun.aliasOrNull: MvUseFunAlias? get() = this.useFunAlias

val MvUseFunAlias.targetTypeOrNull get() = this.pathType
