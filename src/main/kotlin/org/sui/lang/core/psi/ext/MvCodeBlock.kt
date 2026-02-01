package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.sui.lang.MvElementTypes.R_BRACE
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvElementImpl
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvLetStmt
import org.sui.lang.core.psi.MvExprStmt

val MvCodeBlock.returningExpr: MvExpr? get() = this.expr

val MvCodeBlock.rightBrace: PsiElement? get() = this.findLastChildByType(R_BRACE)

val MvCodeBlock.letStmts: List<MvLetStmt> get() = stmtList.filterIsInstance<MvLetStmt>()

abstract class MvCodeBlockMixin(node: ASTNode) : MvElementImpl(node), MvCodeBlock {

    override val expr: MvExpr?
        get() {
            // 检查是否有 ExprStmt 类型的语句
            val exprStmts = this.stmtList.filterIsInstance<MvExprStmt>()
            if (exprStmts.isNotEmpty()) {
                return exprStmts.last().expr
            }
            // 否则，返回 null
            return null
        }

//    override val useStmts: List<MvUseStmt> get() = useStmtList
}
