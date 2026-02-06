package org.sui.lang.core.resolve2.util

import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.MvUseFunAlias
import org.sui.lang.core.psi.MvUseFunStmt
import org.sui.lang.core.psi.MvPublicUseFunStmt
import org.sui.lang.core.psi.ext.aliasOrNull
import org.sui.lang.core.psi.ext.pathOrNull
import org.sui.lang.core.psi.ext.publicUseFunItem
import org.sui.lang.core.psi.ext.useFunItem

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
