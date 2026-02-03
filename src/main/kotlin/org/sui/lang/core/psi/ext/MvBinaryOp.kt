package org.sui.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.sui.lang.core.MOVE_BINARY_OPS
import org.sui.lang.core.psi.MvBinaryOp
import org.sui.lang.core.psi.MvBinaryExpr

val MvBinaryOp.operator: PsiElement
    get() = requireNotNull(node.findChildByType(MOVE_BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val MvBinaryOp.op: String get() = operator.text

fun MvBinaryOp.isBitOp(): Boolean {
    return op in listOf("<<", ">>", "&", "|", "^")
}

fun MvBinaryExpr.isBitOp(): Boolean {
    return this.binaryOp?.isBitOp() ?: false
}
