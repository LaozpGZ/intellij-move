package org.sui.lang.core.types.infer

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
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

            val expectedTypeName = if (expectedTy is TyInteger && expectedTy.isDefault() && (!isAssignmentWithExplicitType(element) && (isAssignmentExprContext(element) || isAbortExprContext(element) || (isIfElseExprContext(element) && isAssignmentExprContext(element)) || isTupleElementContext(element) || isVectorElementContext(element)))) {
                "integer"
            } else {
                expectedTy.name()
            }

            val actualTypeName = if (actualTy is TyInteger && (actualTy.isDefault() && (isIfConditionContext(element) && expectedTy !is TyUnit || isVectorElementContext(element) && isVectorWithoutExplicitType(element) || isTupleElementContext(element) || isAssignmentExprContext(element) || isAbortExprContext(element) || (isIfElseExprContext(element) && isAssignmentExprContext(element))) || isGenericVectorType(expectedTy))) {








                "integer"
            } else if (actualTy is TyInteger && expectedTy is TyUnit) {

                "integer"
            } else {
                actualTy.name()
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

                                if (letStmt.typeAnnotation != null || (letStmt.initializer?.expr?.text?.contains("u") == true || letStmt.initializer?.expr?.text?.contains("i") == true)) {
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

                                val hasTypeAnnotationInInitializer = letStmt.initializer?.expr?.text?.contains("u") == true ||
                                    letStmt.initializer?.expr?.text?.contains("i") == true
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

                    var hasExplicitTypeElement = false
                    for (vectorElement in current.vectorLitItems.exprList) {
                        if (vectorElement != element) {
                            val vectorElementText = vectorElement.text
                            if (vectorElementText.contains("u") || vectorElementText.contains("i")) {
                                hasExplicitTypeElement = true
                                break
                            }
                        }
                    }
                    return !hasExplicitTypeElement
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

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return TypeMismatch(element, folder(expectedTy), folder(actualTy))
        }

        override fun fix(): LocalQuickFix? {
            if (element is MvExpr) {
                if (expectedTy is TyInteger && actualTy is TyInteger
                    && !expectedTy.isDefault() && !actualTy.isDefault()
                ) {
                    if (this.element.isMsl()) return null

                    val expr = element
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
            return "Indexing receiver type should be vector or resource, got '${actualTy.text(fq = false)}'"
        }

        override fun innerFoldWith(folder: TypeFolder): TypeError {
            return IndexingIsNotAllowed(element, folder.fold(actualTy))
        }
    }
}
