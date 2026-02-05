package org.sui.lang.core.types.infer

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.sui.ide.inspections.fixes.IntegerCastFix
import org.sui.ide.presentation.name
import org.sui.ide.presentation.text
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.types.ty.*

sealed class TypeError(open val element: PsiElement) : TypeFoldable<TypeError> {
    abstract fun message(): String

    override fun innerVisitWith(visitor: TypeVisitor): Boolean = true

    open fun range(): TextRange = element.textRange

    open fun fix(): LocalQuickFix? = null

    data class TypeMismatch(
        override val element: PsiElement,
        val expectedTy: Ty,
        val actualTy: Ty
    ) : TypeError(element) {
        override fun message(): String {

            val expectedTypeName = if (expectedTy is TyInteger && expectedTy.isDefault() && (!isAssignmentWithExplicitType(element) && (isAssignmentExprContext(element) || isAbortExprContext(element) || (isIfElseExprContext(element) && isAssignmentExprContext(element)) || isTupleElementContext(element) || isVectorElementContext(element) || isRangeExprContext(element)))) {
                "integer"
            } else {
                expectedTy.name()
            }

            val actualTypeName = when {
                actualTy is TyReference
                        && actualTy.referenced is TyVector
                        && isImplicitDefaultVectorBindingElement(element) -> {
                    val mutPrefix = if (actualTy.mutability.isMut) "&mut " else "&"
                    "${mutPrefix}vector<integer>"
                }
                actualTy is TyVector
                        && isImplicitDefaultVectorBindingElement(element) -> {
                    "vector<integer>"
                }
                actualTy is TyInteger && (actualTy.isDefault() && (isUnsuffixedIntegerLiteralElement(element) || (isIfConditionContext(element) && expectedTy !is TyUnit) || isVectorElementContext(element) && isVectorWithoutExplicitType(element) || isTupleElementContext(element) || isAssignmentExprContext(element) || isAbortExprContext(element) || (isIfElseExprContext(element) && isAssignmentExprContext(element))) || isGenericVectorType(expectedTy)) -> {
                    "integer"
                }
                actualTy is TyInteger && expectedTy is TyUnit -> "integer"
                else -> actualTy.name()
            }

            return when (element) {
                is MvReturnExpr -> "Invalid return type '$actualTypeName', expected '$expectedTypeName'"
                else -> "Incompatible type '$actualTypeName', expected '$expectedTypeName'"
            }
        }

        private fun isAbortExprContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvAbortExpr || current is MvAbortsIfSpecExpr || current is MvAbortsWithSpecExpr) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        private fun isIfElseExprContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvIfExpr && current.elseBlock != null) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        private fun isAssignmentWithExplicitType(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvAssignmentExpr) {

                    val lhs = current.expr
                    if (lhs is MvPathExpr) {
                        val resolved = lhs.path.reference?.resolve()
                        if (resolved is MvPatBinding) {

                            val letStmt = resolved.ancestorStrict<MvLetStmt>()
                            if (letStmt != null) {

                                if (letStmt.typeAnnotation != null || hasSuffixedIntegerLiteral(letStmt.initializer?.expr)) {
                                    return true
                                }
                            }
                        }
                    }
                }
                current = current.parent
            }
            return false
        }

        private fun isAssignmentExprContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvAssignmentExpr) {

                    val lhs = current.expr
                    if (lhs is MvPathExpr) {
                        val resolved = lhs.path.reference?.resolve()
                        if (resolved is MvPatBinding) {

                            val letStmt = resolved.ancestorStrict<MvLetStmt>()
                            if (letStmt != null && letStmt.typeAnnotation == null) {

                                val hasTypeAnnotationInInitializer = hasSuffixedIntegerLiteral(letStmt.initializer?.expr)
                                if (!hasTypeAnnotationInInitializer) {

                                    return true
                                }
                            }
                        }
                    }
                }
                current = current.parent
            }
            return false
        }

        private fun isIfConditionContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvIfExpr) {

                    val condition = current.condition
                    if (condition != null && condition.textRange.contains(element.textRange)) {
                        return true
                    }
                }
                current = current.parent
            }
            return false
        }

        private fun isTupleElementContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvTupleLitExpr) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        private fun isVectorElementContext(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvVectorLitExpr) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        private fun isVectorWithoutExplicitType(element: PsiElement): Boolean {

            var current: PsiElement? = element
            while (current != null) {
                if (current is MvVectorLitExpr && current.typeArgument == null) {

                    val hasExplicitTypeElement = current.vectorLitItems.exprList
                        .filter { it != element }
                        .any { hasSuffixedIntegerLiteral(it) }
                    return !hasExplicitTypeElement
                }
                current = current.parent
            }
            return false
        }

        private fun isUnsuffixedIntegerLiteralElement(element: PsiElement): Boolean {
            val litExpr = element as? MvLitExpr ?: return false
            val literal = litExpr.integerLiteral ?: litExpr.hexIntegerLiteral ?: return false
            return TyInteger.fromSuffixedLiteral(literal) == null
        }

        private fun isImplicitDefaultVectorBindingElement(element: PsiElement): Boolean {
            val pathExpr = (element as? MvPathExpr)
                ?: PsiTreeUtil.findChildOfType(element, MvPathExpr::class.java)
                ?: return false
            val resolved = pathExpr.path.reference?.resolve() as? MvPatBinding ?: return false
            val letStmt = resolved.ancestorStrict<MvLetStmt>() ?: return false
            if (letStmt.typeAnnotation != null) return false
            val initExpr = letStmt.initializer?.expr as? MvVectorLitExpr ?: return false
            return initExpr.vectorLitItems.exprList.none { hasSuffixedIntegerLiteral(it) }
        }

        private fun isRangeExprContext(element: PsiElement): Boolean {
            var current: PsiElement? = element
            while (current != null) {
                if (current is MvRangeExpr) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        private fun isGenericVectorType(expectedTy: Ty): Boolean {

            return when (expectedTy) {
                is TyVector -> expectedTy.item is TyTypeParameter || expectedTy.item is TyInfer.TyVar
                else -> false
            }
        }

        private fun hasSuffixedIntegerLiteral(expr: MvExpr?): Boolean {
            if (expr == null) return false
            if (expr is MvLitExpr && isSuffixedIntegerLiteral(expr)) return true
            return PsiTreeUtil.findChildrenOfType(expr, MvLitExpr::class.java)
                .any { isSuffixedIntegerLiteral(it) }
        }

        private fun isSuffixedIntegerLiteral(litExpr: MvLitExpr): Boolean {
            val literal = litExpr.integerLiteral ?: litExpr.hexIntegerLiteral ?: return false
            return TyInteger.fromSuffixedLiteral(literal) != null
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return TypeMismatch(element, folder(expectedTy), folder(actualTy))
        }

        override fun fix(): LocalQuickFix? {
            if (element is MvExpr) {
                if (expectedTy is TyInteger && actualTy is TyInteger) {
                    if (this.element.isMsl()) return null

                    val expr = element
                    if (actualTy.isDefault() && isImplicitDefaultIntegerExpr(expr)) return null
                    val inference = expr.inference(false) ?: return null

                    if (expr is MvParensExpr && expr.expr is MvCastExpr) {
                        val castExpr = expr.expr as MvCastExpr
                        val originalTy = inference.getExprType(castExpr.expr) as? TyInteger ?: return null
                        if (originalTy == expectedTy) {
                            return IntegerCastFix.RemoveCast(castExpr)
                        } else {
                            return IntegerCastFix.ChangeCast(castExpr, expectedTy)
                        }
                    }
                    return IntegerCastFix.AddCast(element, expectedTy)
                }
            }
            return null
        }

        private fun isImplicitDefaultIntegerExpr(element: PsiElement): Boolean {
            val expr = element as? MvExpr ?: return false
            return when (expr) {
                is MvLitExpr -> isUnsuffixedIntegerLiteralElement(expr)
                is MvPathExpr -> {
                    val resolved = expr.path.reference?.resolve() as? MvPatBinding ?: return false
                    val letStmt = resolved.ancestorStrict<MvLetStmt>() ?: return false
                    if (letStmt.typeAnnotation != null) return false
                    val initExpr = letStmt.initializer?.expr as? MvLitExpr ?: return false
                    isUnsuffixedIntegerLiteralElement(initExpr)
                }
                else -> false
            }
        }
    }

    data class UnsupportedBinaryOp(
        override val element: PsiElement,
        val ty: Ty,
        val op: String
    ) : TypeError(element) {
        override fun message(): String {
            return "Invalid argument to '$op': " +
                    "expected integer type, but found '${ty.text()}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return UnsupportedBinaryOp(element, folder(ty), op)
        }
    }

    data class IncompatibleArgumentsToBinaryExpr(
        override val element: PsiElement,
        val leftTy: Ty,
        val rightTy: Ty,
        val op: String,
    ) : TypeError(element) {
        override fun message(): String {
            fun formatType(ty: Ty, otherTy: Ty): String {

                if (ty is TyInteger && otherTy is TyInteger) {
                    return ty.text()
                }

                if (ty is TyInteger && ty.isDefault()) {
                    return "integer"
                }
                return ty.text()
            }

            return "Incompatible arguments to '$op': " +
                    "'${formatType(leftTy, rightTy)}' and '${formatType(rightTy, leftTy)}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return IncompatibleArgumentsToBinaryExpr(element, folder(leftTy), folder(rightTy), op)
        }
    }

    data class InvalidUnpacking(
        override val element: PsiElement,
        val assignedTy: Ty,
    ) : TypeError(element) {
        override fun message(): String {
            return when {
                element is MvPatStruct &&
                        (assignedTy !is TyAdt && assignedTy !is TyTuple) -> {
                    "Assigned expr of type '${assignedTy.text(fq = false)}' " +
                            "cannot be unpacked with struct pattern"
                }
                element is MvPatTuple &&
                        (assignedTy !is TyAdt && assignedTy !is TyTuple) -> {
                    "Assigned expr of type '${assignedTy.text(fq = false)}' " +
                            "cannot be unpacked with tuple pattern"
                }
                else -> "Invalid unpacking. Expected ${assignedTy.assignedTyFormText()}"
            }
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return InvalidUnpacking(element, folder(assignedTy))
        }

        private fun Ty.assignedTyFormText(): String {
            return when (this) {
                is TyTuple -> {
                    val expectedForm = this.types.joinToString(", ", "(", ")") { "_" }
                    "tuple binding of length ${this.types.size}: $expectedForm"
                }
                is TyAdt -> "struct binding of type ${this.text(true)}"
                else -> "a single variable"
            }
        }
    }

    data class CircularType(
        override val element: PsiElement,
        val itemElement: MvItemElement
    ) : TypeError(element) {
        override fun message(): String {
            return "Circular reference of type '${itemElement.name}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError = this
    }

    data class ExpectedNonReferenceType(
        override val element: PsiElement,
        val actualTy: Ty,
    ) : TypeError(element) {

        override fun message(): String {
            return "Expected a single non-reference type, but found: '${actualTy.text(fq = false)}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return ExpectedNonReferenceType(element, folder.fold(actualTy))
        }
    }

    data class InvalidDereference(
        override val element: PsiElement,
        val actualTy: Ty
    ): TypeError(element) {
        override fun message(): String {
            val actualTypeName = if (actualTy is TyInteger && actualTy.isDefault()) {
                "integer"
            } else {
                actualTy.text(fq = false)
            }
            return "Invalid dereference. Expected '&_' but found '$actualTypeName'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return InvalidDereference(element, folder.fold(actualTy))
        }
    }

    data class IndexingIsNotAllowed(
        override val element: PsiElement,
        val actualTy: Ty,
    ): TypeError(element) {
        override fun message(): String {
            return "Indexing receiver type should be vector or support #[syntax(index)], got '${actualTy.text(fq = false)}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return IndexingIsNotAllowed(element, folder.fold(actualTy))
        }
    }
}
