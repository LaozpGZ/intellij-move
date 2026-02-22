package org.sui.lang.core.resolve2.util

import org.sui.lang.core.psi.MvPath
import org.sui.lang.core.psi.MvUseAlias
import org.sui.lang.core.psi.MvUseSpeck
import org.sui.lang.core.psi.MvUseStmt
import org.sui.lang.core.psi.ext.childOfType
import org.sui.lang.core.psi.ext.childrenOfType

fun interface LeafUseSpeckConsumer {
    fun consume(path: MvPath, useAlias: MvUseAlias?): Boolean
}

fun MvUseStmt.forEachLeafSpeck(consumer: LeafUseSpeckConsumer) {
    val rootUseSpeck = this.childOfType<MvUseSpeck>() ?: return
    rootUseSpeck.forEachLeafSpeck(consumer)
}

private fun MvUseSpeck.forEachLeafSpeck(consumer: LeafUseSpeckConsumer): Boolean {
    val useGroup = this.useGroup
    if (useGroup == null) {
        return consumer.consume(this.path, this.useAlias)
    }

    for (childSpeck in useGroup.childrenOfType<MvUseSpeck>()) {
        if (childSpeck.forEachLeafSpeck(consumer)) return true
    }
    return false
}
