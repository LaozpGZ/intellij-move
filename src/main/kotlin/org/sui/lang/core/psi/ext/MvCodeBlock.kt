package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.sui.lang.MvElementTypes.R_BRACE
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvElementImpl
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvLetStmt
import org.sui.lang.core.psi.MvExprStmt
import org.sui.lang.core.psi.MvItemSpecBlockExpr

val MvCodeBlock.returningExpr: MvExpr? get() = this.expr

val MvCodeBlock.rightBrace: PsiElement? get() = this.findLastChildByType(R_BRACE)

val MvCodeBlock.letStmts: List<MvLetStmt> get() = stmtList.filterIsInstance<MvLetStmt>()

abstract class MvCodeBlockMixin(node: ASTNode) : MvElementImpl(node), MvCodeBlock {

    override val expr: MvExpr?
        get() {
            // Traverse all statements to find the last ExprStmt that is not spec-related
            var lastExprStmt: MvExprStmt? = null
            for (stmt in this.stmtList) {
                if (stmt is MvExprStmt) {
                    // Check if it's a spec block related expression (using type check instead of text check)
                    val isSpecBlock = stmt.expr is MvItemSpecBlockExpr
                    if (!isSpecBlock) {
                        lastExprStmt = stmt
                    }
                }
            }
            if (lastExprStmt != null) {
                return lastExprStmt.expr
            }

            // If no non-spec block ExprStmt is found, check for direct MvExpr type children
            for (child in this.children) {
                if (child is MvExpr && child !is MvItemSpecBlockExpr) {
                    return child
                }
            }

            // Otherwise, return null
            return null
        }

//    override val useStmts: List<MvUseStmt> get() = useStmtList
}
