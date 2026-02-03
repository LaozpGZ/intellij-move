package org.sui.lang.core.types.infer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.sui.cli.settings.debugErrorOrFallback
import org.sui.cli.settings.isDebugModeEnabled
import org.sui.cli.settings.moveSettings
import org.sui.ide.formatter.impl.location
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.core.resolve.collectMethodOrPathResolveVariants
import org.sui.lang.core.resolve.processAll
import org.sui.lang.core.resolve.ref.NONE
import org.sui.lang.core.resolve.resolveSingleResolveVariant
import org.sui.lang.core.resolve2.processMethodResolveVariants
import org.sui.lang.core.resolve2.ref.InferenceCachedPathElement
import org.sui.lang.core.resolve2.ref.ResolutionContext
import org.sui.lang.core.resolve2.ref.resolveAliases
import org.sui.lang.core.resolve2.ref.resolvePathRaw
import org.sui.lang.core.resolve2.resolveBindingForFieldShorthand
import org.sui.lang.core.types.ty.*
import org.sui.lang.core.resolve2.PreImportedModuleService
import org.sui.lang.core.types.ty.TyReference.Companion.autoborrow
import org.sui.stdext.RsResult
import org.sui.stdext.chain

class TypeInferenceWalker(
    val ctx: InferenceContext,
    val project: Project,
    private val returnTy: Ty
) {

    val msl: Boolean get() = ctx.msl

    fun <T> mslScope(action: () -> T): T {
        if (ctx.msl) return action()
        ctx.msl = true
        val snapshot = ctx.startSnapshot()
        try {
            return action()
        } finally {
            ctx.msl = false
            snapshot.rollback()
        }
    }

    fun extractParameterBindings(owner: MvInferenceContextOwner) {
        val bindings = when (owner) {
            is MvFunctionLike -> owner.parametersAsBindings
            is MvItemSpec -> {
                val specItem = owner.item
                when (specItem) {
                    is MvFunction -> {
                        specItem.parametersAsBindings
                            .chain(specItem.specFunctionResultParameters.map { it.patBinding })
                            .toList()
                    }
                    else -> emptyList()
                }
            }
            is MvSchema -> owner.fieldsAsBindings
            else -> emptyList()
        }
        for (binding in bindings) {
            val bindingContext = binding.owner
            val ty = when (bindingContext) {
                null -> TyUnknown
                is MvFunctionParameter -> bindingContext.type?.loweredType(msl) ?: TyUnknown
                is MvSchemaFieldStmt -> bindingContext.type?.loweredType(msl) ?: TyUnknown
                else -> {
                    debugErrorOrFallback(
                        "${bindingContext.elementType} binding is not inferred",
                        TyUnknown
                    )
                }
            }
            this.ctx.writePatTy(binding, ty)
        }
    }

    fun inferFnBody(block: AnyBlock): Ty = block.inferBlockCoercableTo(returnTy)
    fun inferSpec(block: AnyBlock): Ty =
        mslScope { block.inferBlockType(Expectation.NoExpectation) }

    private fun AnyBlock.inferBlockCoercableTo(expectedTy: Ty): Ty {
        return this.inferBlockCoercableTo(Expectation.maybeHasType(expectedTy))
    }

    private fun AnyBlock.inferBlockCoercableTo(expected: Expectation): Ty {
        return this.inferBlockType(expected, coerce = true)
    }

    private fun AnyBlock.inferBlockType(expected: Expectation, coerce: Boolean = false): Ty {
        val stmts = when (this) {
            is MvSpecCodeBlock, is MvModuleSpecBlock -> {
                // reorder stmts, move let stmts to the top, then let post, then others
                this.stmtList.sortedBy {
                    when {
                        it is MvLetStmt && !it.post -> 0
                        it is MvLetStmt && it.post -> 1
                        else -> 2
                    }
                }
            }
            else -> this.stmtList
        }

        println("inferBlockType: Processing statements: ${stmts.map { it.text }}")
        stmts.forEach { processStatement(it) }

        // Process all types of expressions
        // These expressions may exist directly as statements in the PSI tree without being wrapped in ExprStmt
        // We need to find these expressions and perform type inference on them
        // However, we need to avoid reprocessing already processed expressions, especially MvAbortExpr
        this.descendantsOfType<MvExpr>().forEach { expr ->
            if (!ctx.isTypeInferred(expr) && expr !is MvAbortExpr) {
                expr.inferType()
            }
        }

        // If the block contains statements like return, break, continue, or abort, return TyNever
        if (hasNeverTypeStatement(stmts)) {
            println("inferBlockType: Found never type statement, returning TyNever")
            return TyNever
        }

        val tailExpr = this.expr
        val expectedTy = expected.onlyHasTy(ctx)

        return if (tailExpr == null) {
            // No tail expression: the block evaluates to TyUnit.
            if (coerce && expectedTy != null) {
                coerce(this.rBrace ?: this, TyUnit, expectedTy)
            }
            TyUnit
        } else {
            // Handle tail expression
            val ty = tailExpr.inferType()

            // Ensure the type of the tail expression is correctly written to the context
            // If it's an MvAbortExpr type, we should preserve TyNever and not overwrite it
            if (tailExpr is MvAbortExpr) {
                println("Tail expr is MvAbortExpr, type should be TyNever, current: $ty")
                val neverTy = TyNever
                ctx.writeExprTy(tailExpr, neverTy)
                return neverTy
            } else {

                val actualTy = ctx.getExprType(tailExpr) ?: ty

                println("Block tail expr: ${tailExpr.text}, actual type: $actualTy, expected type: $expectedTy")


                if (coerce && expectedTy != null) {

                    var isInsideExprStmt = false
                    var current: PsiElement? = this
                    while (current != null) {
                        if (current is MvExprStmt) {
                            isInsideExprStmt = true
                            break
                        }
                        current = current.parent
                    }

                    if (!isInsideExprStmt) {

                        if (!coerce(tailExpr, actualTy, expectedTy)) {


                        }
                    }
                }




                var shouldReturnTailType = true
                if (expectedTy == null) {
                    var current: PsiElement? = this
                    var isInsideIfElseExpr = false
                    while (current != null) {
                        if (current is MvIfExpr && current.elseBlock != null) {
                            isInsideIfElseExpr = true
                            shouldReturnTailType = true
                            break
                        }
                        if (current is MvExprStmt) {

                            shouldReturnTailType = false
                            break
                        }

                        if (current is MvAssignmentExpr && current.initializer.expr == this) {
                            shouldReturnTailType = true
                            break
                        }
                        current = current.parent
                    }
                }


                if (!shouldReturnTailType) {
                    ctx.writeExprTy(tailExpr, actualTy)
                    return TyUnit
                }


                ctx.writeExprTy(tailExpr, actualTy)
                return actualTy
            }
        }
    }

    private fun hasNeverTypeStatement(stmts: List<MvStmt>): Boolean {
        for (stmt in stmts) {
            when (stmt) {
                is MvExprStmt -> {
                    val expr = stmt.expr
                    // Check whether the expression type is TyNever.
                    println("hasNeverTypeStatement: Checking expr type: ${expr.text}")
                    if (ctx.isTypeInferred(expr)) {
                        val ty = ctx.getExprType(expr)
                        println("hasNeverTypeStatement: Type from context: $ty")
                        if (ty is TyNever) {
                            println("hasNeverTypeStatement: Found TyNever, returning true")
                            return true
                        }
                    } else {
                        println("hasNeverTypeStatement: Type not inferred yet, calling inferType()")
                        // If not inferred yet, infer it now.
                        val ty = expr.inferType()
                        println("hasNeverTypeStatement: Inferred type: $ty")
                        if (ty is TyNever) {
                            println("hasNeverTypeStatement: Found TyNever after inferType(), returning true")
                            return true
                        }
                    }
                }
            }
        }
        println("hasNeverTypeStatement: No TyNever found, returning false")
        return false
    }

    fun resolveTypeVarsIfPossible(ty: Ty): Ty = ctx.resolveTypeVarsIfPossible(ty)

    private fun processStatement(stmt: MvStmt) {
        when (stmt) {
            is MvLetStmt -> {
                println("=== Processing MvLetStmt ===")
                println("stmt.text: ${stmt.text}")
                val explicitTy = stmt.type?.loweredType(msl)
                val expr = stmt.initializer?.expr
                val pat = stmt.pat
                val inferredTy =
                    if (expr != null) {
                        val inferredTy = expr.inferType(Expectation.maybeHasType(explicitTy))
                        println("expr.inferType: $inferredTy")
                        val coercedTy = if (explicitTy != null && coerce(expr, inferredTy, explicitTy)) {
                            println("Coerced to explicitTy: $explicitTy")
                            explicitTy
                        } else {
                            println("Using inferredTy: $inferredTy")
                            inferredTy
                        }
                        coercedTy
                    } else {
                        pat?.anonymousTyVar() ?: TyUnknown
                    }
                println("Final inferredTy: $inferredTy")
                pat?.extractBindings(
                    this,
                    explicitTy ?: resolveTypeVarsIfPossible(inferredTy)
                )
            }
            is MvSchemaFieldStmt -> {
                val binding = stmt.patBinding
                val ty = stmt.type?.loweredType(msl) ?: TyUnknown
                ctx.writePatTy(binding, resolveTypeVarsIfPossible(ty))
            }
            is MvIncludeStmt -> inferIncludeStmt(stmt)
            is MvUpdateSpecStmt -> inferUpdateStmt(stmt)
            is MvExprStmt -> {
                println("Processing ExprStmt: ${stmt.text}")
                val expr = stmt.expr
                println("Expr type: ${expr.javaClass.simpleName}")
                if (expr is MvAbortExpr) {
                    // Ensure MvAbortExpr is typed as TyNever.
                    val ty = expr.inferType()
                    println("MvAbortExpr type: $ty")
                    ctx.writeExprTy(expr, ty)
                } else {

                    val ty = expr.inferType()
                    println("Inferred type: $ty")
                    ctx.writeExprTy(expr, ty)



                }
            }
            is MvSpecExprStmt -> stmt.expr.inferType()
            is MvPragmaSpecStmt -> {
                stmt.pragmaAttributeList.forEach { it.expr?.inferType() }
            }
        }
    }

    private fun MvExpr.inferType(expected: Ty?): Ty = this.inferType(Expectation.maybeHasType(expected))

    private fun MvExpr.inferType(expected: Expectation = Expectation.NoExpectation): Ty {
        try {
            return inferExprTy(this, expected)
        } catch (e: UnificationError) {
            if (e.context == null) {
                e.context = PsiErrorContext.fromElement(this)
            }
            throw e
        } catch (e: InferenceError) {
            if (e.context == null) {
                e.context = PsiErrorContext.fromElement(this)
            }
            throw e
        }
    }

    // returns inferred
    private fun MvExpr.inferTypeCoercableTo(expected: Ty): Ty {
        val inferred = this.inferType(expected)
        coerce(this, inferred, expected)
        return inferred
    }

    // returns inferred
    private fun MvExpr.inferTypeCoercableTo(expected: Expectation): Ty {
        val expectedTy = expected.onlyHasTy(ctx)
        return if (expectedTy != null) {
            this.inferTypeCoercableTo(expectedTy)
        } else {
            this.inferType()
        }
    }

    // returns expected
    private fun MvExpr.inferTypeCoerceTo(expected: Ty): Ty {
        val inferred = this.inferType(expected)
        return if (coerce(this, inferred, expected)) expected else inferred
    }

    private fun inferExprTy(
        expr: MvExpr,
        expected: Expectation = Expectation.NoExpectation
    ): Ty {
        ProgressManager.checkCanceled()
        if (ctx.isTypeInferred(expr)) {
            return ctx.getExprType(expr)
        }

        expected.tyAsNullable(this.ctx)?.let {
            when (expr) {
                is MvStructLitExpr,
                is MvPathExpr,
                is MvDotExpr,
                is MvCallExpr,
                is MvRefExpr -> this.ctx.writeExprExpectedTy(expr, it)
            }
        }

        println("=== inferExprTy ===")
        println("expr.text: ${expr.text}")
        println("expr.class: ${expr.javaClass.simpleName}")
        val exprTy = when (expr) {
            is MvAbortExpr -> {
                expr.expr?.let {

                    val argTy = it.inferType()
                    if (argTy !is TyInteger && argTy !is TyInfer.IntVar && argTy !is TyNum) {
                        ctx.reportTypeError(TypeError.TypeMismatch(it, TyInteger.DEFAULT, argTy))
                    }
                }
                val ty = TyNever
                ctx.writeExprTy(expr, ty)
                println("MvAbortExpr: returning TyNever")
                // Return TyNever directly to avoid later mslScopeRefined handling.
                return ty
            }
            is MvPathExpr -> inferPathExprTy(expr, expected)
            is MvBorrowExpr -> inferBorrowExprTy(expr, expected)
            is MvCallExpr -> inferCallExprTy(expr, expected)
            is MvAssertMacroExpr -> inferMacroCallExprTy(expr)
            is MvMacroCallExpr -> inferMacroCallExprTy(expr)
            is MvStructLitExpr -> inferStructLitExprTy(expr, expected)
            is MvVectorLitExpr -> inferVectorLitExpr(expr, expected)
            is MvIndexExpr -> inferIndexExprTy(expr)

            is MvDotExpr -> inferDotExprTy(expr, expected)
            is MvDerefExpr -> inferDerefExprTy(expr)
            is MvLitExpr -> {
                val ty = inferLitExprTy(expr, expected)
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvTupleLitExpr -> inferTupleLitExprTy(expr, expected)
            is MvLambdaExpr -> inferLambdaExpr(expr, expected)

            is MvMoveExpr -> expr.expr?.inferType() ?: TyUnknown
            is MvCopyExpr -> expr.expr?.inferType() ?: TyUnknown

            is MvItemSpecBlockExpr -> expr.specBlock?.let { inferSpec(it) } ?: TyUnknown

            is MvCastExpr -> {
                expr.expr.inferType()
                val ty = expr.type.loweredType(msl)
                expected.onlyHasTy(this.ctx)?.let {
                    this.ctx.combineTypes(it, ty)
                }
                ty
            }
            is MvParensExpr -> expr.expr?.inferType(expected) ?: TyUnknown

            is MvBinaryExpr -> inferBinaryExprTy(expr)
            is MvBangExpr -> {
                expr.expr?.inferType(Expectation.maybeHasType(TyBool))
                TyBool
            }

            is MvIfExpr -> {
                val ty = inferIfExprTy(expr, expected)
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvWhileExpr -> {
                val ty = inferWhileExpr(expr)
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvLoopExpr -> {
                val ty = inferLoopExpr(expr)
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvForExpr -> {
                val ty = inferForExpr(expr)
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvReturnExpr -> {
                expr.expr?.inferTypeCoercableTo(returnTy)
                val ty = TyNever
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvContinueExpr -> {
                val ty = TyNever
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvBreakExpr -> {
                val ty = TyNever
                ctx.writeExprTy(expr, ty)
                ty
            }
            is MvAbortExpr -> {
                expr.expr?.inferTypeCoercableTo(TyInteger.DEFAULT)
                val ty = TyNever
                ctx.writeExprTy(expr, ty)

                println("MvAbortExpr: returning TyNever")
                return ty
            }
            is MvCodeBlockExpr -> expr.codeBlock.inferBlockType(expected, coerce = true)
            is MvAssignmentExpr -> inferAssignmentExprTy(expr)
            is MvBoolSpecExpr -> inferBoolSpecExpr(expr)
            is MvQuantExpr -> inferQuantExprTy(expr)
            is MvRangeExpr -> inferRangeExprTy(expr)
            is MvModifiesSpecExpr -> {
                expr.expr?.inferType()
                TyUnit
            }
            is MvAbortsWithSpecExpr -> {
                expr.exprList.forEach { it.inferTypeCoercableTo(TyInteger.DEFAULT) }
                TyUnit
            }

            is MvMatchExpr -> inferMatchExprTy(expr)

            else -> inferenceErrorOrFallback(expr, TyUnknown)
        }


        // If we already returned TyNever, avoid overwriting it.
        if (exprTy is TyNever) {
            println("Already TyNever, returning without writing to ctx")
            return exprTy
        }
        val refinedExprTy = exprTy.mslScopeRefined(msl)
        println("Writing type to ctx for expr: ${expr.text}, type: ${refinedExprTy}")
        println("exprTy.class: ${exprTy.javaClass.simpleName}")
        println("refinedExprTy.class: ${refinedExprTy.javaClass.simpleName}")
        println("exprTy value: ${exprTy}")
        ctx.writeExprTy(expr, refinedExprTy)
        val retrievedTy = ctx.getExprType(expr)
        println("Retrieved type from ctx: ${retrievedTy}")

        println("=== exprTypes ===")
        ctx.exprTypes.forEach {
            println("${it.key.text} -> ${it.value}, HashCode: ${it.key.hashCode()}")
        }
        println("=== exprTypes end ===")
        println("=== inferExprTy end ===")
        return refinedExprTy
    }

    private fun inferBoolSpecExpr(expr: MvBoolSpecExpr): Ty {
        when (expr) {
            is MvAssertSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
            is MvAssumeSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
            is MvAxiomSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
            is MvAbortsIfSpecExpr -> {
                expr.expr?.inferTypeCoercableTo(TyBool)
                expr.abortsIfWith?.expr?.inferTypeCoercableTo(TyInteger.DEFAULT)
            }
            is MvEnsuresSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
            is MvRequiresSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
            is MvInvariantSpecExpr -> expr.expr?.inferTypeCoercableTo(TyBool)
        }
        return TyUnit
    }

    private fun inferPathExprTy(pathExpr: MvPathExpr, expected: Expectation): Ty {
        // special-case `result` inside item spec
//        if (msl && refExpr.path.text == "result") {
//            val funcItem = refExpr.ancestorStrict<MvItemSpec>()?.funcItem
//            if (funcItem != null) {
//                return funcItem.rawReturnType(true)
//            }
//        }
//        val path = pathExpr.path
//        val resolveVariants = resolvePathRaw(path)
//        ctx.writePath(path, resolveVariants.map { ResolvedPath.from(it, path)})

        println("=== inferPathExprTy ===")
        println("pathExpr.text: ${pathExpr.text}")
        println("pathExpr.class: ${pathExpr.javaClass.simpleName}")
        println("pathExpr.hashCode(): ${pathExpr.hashCode()}")

        val expectedType = expected.onlyHasTy(ctx)
        val item = resolvePathElement(pathExpr, expectedType) ?: return TyUnknown

        println("item: ${item}")
        println("item.class: ${item.javaClass.simpleName}")
        println("item.hashCode(): ${item.hashCode()}")

        val ty = when (item) {
            is MvPatBinding -> {
                val bindingTy = ctx.getBindingType(item)
                println("Binding type: $bindingTy")

                if (msl && (bindingTy is TyInteger || bindingTy is TyInfer.IntVar)) {
                    TyNum
                } else {
                    bindingTy.mslScopeRefined(msl)
                }
            }
            is MvConst -> item.type?.loweredType(msl) ?: TyUnknown
            is MvGlobalVariableStmt -> item.type?.loweredType(true) ?: TyUnknown
            is MvNamedFieldDecl -> item.type?.loweredType(msl) ?: TyUnknown
            is MvStruct -> {
                if (project.moveSettings.enableIndexExpr && pathExpr.parent is MvIndexExpr) {
                    TyLowering.lowerPath(pathExpr.path, item, ctx.msl)
                } else {
                    // invalid statements
                    TyUnknown
                }
            }
            is MvEnumVariant -> item.enumItem.declaredType(ctx.msl)
            is MvModule -> TyUnknown
            else -> debugErrorOrFallback(
                "Referenced item ${item.elementType} " +
                        "of ref expr `${pathExpr.text}` at ${pathExpr.location} cannot be inferred into type",
                TyUnknown
            )
        }
        println("Computed ty: $ty")
        ctx.writeExprTy(pathExpr, ty)
        println("Written to exprTypes")
        println("=== inferPathExprTy end ===")
        return ty
    }

    fun resolvePathElement(
        pathElement: InferenceCachedPathElement,
        expectedType: Ty?
    ): MvNamedElement? {
        val path = pathElement.path
        val resolvedItems = resolvePathRaw(path, expectedType).map { ResolvedItem.from(it, path) }
        ctx.writePath(path, resolvedItems)
        // resolve aliases
        return resolvedItems.singleOrNull { it.isVisible }
            ?.element
            ?.let { resolveAliases(it) }
    }

    private fun inferAssignmentExprTy(assignExpr: MvAssignmentExpr): Ty {
        val lhsTy = assignExpr.expr.inferType()
        val rhsExpr = assignExpr.initializer.expr
        if (rhsExpr != null) {
            val rhsTy = if (rhsExpr is MvIfExpr) {

                rhsExpr.inferType(expected = Expectation.maybeHasType(lhsTy))
            } else {
                rhsExpr.inferType()
            }

            if (rhsExpr is MvCodeBlockExpr) {

                val tailExpr = rhsExpr.codeBlock.expr
                if (tailExpr != null) {
                    val tailTy = ctx.getExprType(tailExpr)

                    if (lhsTy is TyInteger && tailTy is TyInteger) {
                        if (lhsTy.kind != tailTy.kind) {
                            ctx.reportTypeError(TypeError.TypeMismatch(tailExpr, lhsTy, tailTy))
                        }
                    } else if (!ctx.tryCoerce(tailTy, lhsTy).isOk) {
                        ctx.reportTypeError(TypeError.TypeMismatch(tailExpr, lhsTy, tailTy))
                    }
                }
            } else if (rhsExpr is MvTupleLitExpr && lhsTy is TyTuple) {

                val rhsElements = rhsExpr.exprList
                val lhsElements = lhsTy.types

                rhsElements.forEachIndexed { index, expr ->
                    if (index < lhsElements.size) {
                        val elementTy = ctx.getExprType(expr)
                        val expectedTy = lhsElements[index]

                        if (expectedTy is TyInteger && elementTy is TyInteger) {
                            if (expectedTy.kind != elementTy.kind) {
                                ctx.reportTypeError(TypeError.TypeMismatch(expr, expectedTy, elementTy))
                            }
                        } else if (!ctx.tryCoerce(elementTy, expectedTy).isOk) {
                            ctx.reportTypeError(TypeError.TypeMismatch(expr, expectedTy, elementTy))
                        }
                    }
                }
            } else if (rhsExpr !is MvIfExpr) {

                if (!ctx.tryCoerce(rhsTy, lhsTy).isOk) {
                    ctx.reportTypeError(TypeError.TypeMismatch(rhsExpr, lhsTy, rhsTy))
                }
            }

        }
        return TyUnit
    }

    private fun inferBorrowExprTy(borrowExpr: MvBorrowExpr, expected: Expectation): Ty {
        val innerExpr = borrowExpr.expr ?: return TyUnknown
        val expectedInnerTy = (expected.onlyHasTy(ctx) as? TyReference)?.referenced
        val hint = Expectation.maybeHasType(expectedInnerTy)

        val innerExprTy = innerExpr.inferType(expected = hint)
//        val innerExprTy = inferExprTy(innerExpr, hint)
        val innerRefTy = when (innerExprTy) {
            is TyReference, is TyTuple -> {
                ctx.reportTypeError(TypeError.ExpectedNonReferenceType(innerExpr, innerExprTy))
                TyUnknown
            }
            else -> innerExprTy
        }

        val mutability = Mutability.valueOf(borrowExpr.isMut)
        return TyReference(innerRefTy, mutability, ctx.msl)
    }

    private fun inferLambdaExpr(lambdaExpr: MvLambdaExpr, expected: Expectation): Ty {
        val bindings = lambdaExpr.patBindingList
        val lambdaTy =
            (expected.onlyHasTy(this.ctx) as? TyLambda) ?: TyLambda.unknown(bindings.size)

        for ((i, binding) in lambdaExpr.patBindingList.withIndex()) {
            val ty = lambdaTy.paramTypes.getOrElse(i) { TyUnknown }
            ctx.writePatTy(binding, ty)
        }
        lambdaExpr.expr?.inferTypeCoercableTo(lambdaTy.retType)
        return TyUnknown
    }

    private fun inferCallExprTy(callExpr: MvCallExpr, expected: Expectation): Ty {
        val path = callExpr.path
        val namedItem = resolvePathElement(callExpr, expectedType = null)
        val baseTy =
            when (namedItem) {
                is MvFunctionLike -> {
                    val (itemTy, _) = ctx.instantiateMethodOrPath<TyFunction>(path, namedItem)
                        ?: return TyUnknown
                    itemTy
                }
                is MvPatBinding -> {
                    ctx.getBindingType(namedItem) as? TyLambda
                        ?: TyFunction.unknownTyFunction(callExpr.project, callExpr.valueArguments.size)
                }
                else -> TyFunction.unknownTyFunction(callExpr.project, callExpr.valueArguments.size)
            }
        val funcTy = ctx.resolveTypeVarsIfPossible(baseTy) as TyCallable



        val expectedInputTys = if (expected.onlyHasTy(ctx) is TyUnit && callExpr.parent is MvExprStmt) {
            emptyList()
        } else {
            expectedInputsForExpectedOutput(expected, funcTy.retType, funcTy.paramTypes)
        }


        val isAbortCall = callExpr.path.text == "abort"
        val hasExplicitTypeParameters = if (isAbortCall) {
            false
        } else {
            callExpr.path.typeArguments.isNotEmpty()
        }

        inferArgumentTypes(
            funcTy.paramTypes,
            expectedInputTys,
            callExpr.argumentExprs.map { InferArg.ArgExpr(it) },
            hasExplicitTypeParameters)

        writeCallableType(callExpr, funcTy, method = false)

        return funcTy.retType
    }

    private fun writeCallableType(callable: MvCallable, funcTy: TyCallable, method: Boolean) {
        // callableType TyVar are meaningful mostly for "needs type annotation" error.
        // if value parameter is missing, we don't want to show that error, so we cover
        // unknown parameters with TyUnknown here
        ctx.freezeUnification {
            val valueArguments = callable.valueArguments
            val paramTypes = funcTy.paramTypes.drop(if (method) 1 else 0)
            for ((i, paramType) in paramTypes.withIndex()) {
                val argumentExpr = valueArguments.getOrNull(i)?.expr
                if (argumentExpr == null) {
                    paramType.visitInferTys {
                        ctx.combineTypes(it, TyUnknown); true
                    }
                }
            }
            ctx.writeCallableType(callable, ctx.resolveTypeVarsIfPossible(funcTy as Ty))
        }
    }

    fun writePatTy(psi: MvPat, ty: Ty): Unit =
        ctx.writePatTy(psi, ty)

    fun getResolvedPath(path: MvPath): List<ResolvedItem> {
        return ctx.resolvedPaths[path] ?: emptyList()
    }

    fun inferDotFieldTy(receiverTy: Ty, dotField: MvStructDotField): Ty {
        val tyAdt =
            receiverTy.derefIfNeeded() as? TyAdt ?: return TyUnknown

        val field =
            resolveSingleResolveVariant(dotField.referenceName) {
                processNamedFieldVariants(dotField, tyAdt, msl, it)
            } as? MvNamedFieldDecl
        ctx.resolvedFields[dotField] = field

        val fieldTy = field?.type?.loweredType(msl)?.substitute(tyAdt.typeParameterValues)
        return fieldTy ?: TyUnknown
    }

    fun inferMethodCallTy(receiverTy: Ty, methodCall: MvMethodCall, expected: Expectation): Ty {

        val resolutionCtx = ResolutionContext(methodCall, isCompletion = false)
        val resolvedMethods =
            collectMethodOrPathResolveVariants(methodCall, resolutionCtx) {
                processMethodResolveVariants(methodCall, receiverTy, msl, it)
            }
        val genericItem =
            resolvedMethods.filter { it.isVisible }.mapNotNull { it.element as? MvNamedElement }.singleOrNull()
        ctx.resolvedMethodCalls[methodCall] = genericItem

        val baseTy =
            when (genericItem) {
                is MvFunction -> {
                    val (itemTy, _) =
                        ctx.instantiateMethodOrPath<TyFunction>(methodCall, genericItem) ?: return TyUnknown
                    itemTy
                }
                else -> {
                    // 1 for `self`
                    TyFunction.unknownTyFunction(methodCall.project, 1 + methodCall.valueArguments.size)
                }
            }
        val methodTy = ctx.resolveTypeVarsIfPossible(baseTy) as TyCallable

        val expectedInputTys =
            expectedInputsForExpectedOutput(expected, methodTy.retType, methodTy.paramTypes)

        inferArgumentTypes(
            methodTy.paramTypes,
            expectedInputTys,
            listOf(InferArg.SelfType(receiverTy))
                    + methodCall.argumentExprs.map { InferArg.ArgExpr(it) }
        )

        writeCallableType(methodCall, methodTy, method = true)

        return methodTy.retType
    }

    sealed class InferArg {
        data class SelfType(val selfTy: Ty): InferArg()
        data class ArgExpr(val expr: MvExpr?): InferArg()
    }

    private fun inferArgumentTypes(
        formalInputTys: List<Ty>,
        expectedInputTys: List<Ty>,
        inferArgs: List<InferArg>,
        hasExplicitTypeParameters: Boolean = false,
    ) {
        for ((i, inferArg) in inferArgs.withIndex()) {
            val formalInputTy = formalInputTys.getOrNull(i) ?: TyUnknown
            val expectedInputTy = expectedInputTys.getOrNull(i) ?: formalInputTy
            val expectation = Expectation.maybeHasType(expectedInputTy)
            val expectedTy =
                resolveTypeVarsIfPossible(expectation.onlyHasTy(ctx) ?: formalInputTy)
            when (inferArg) {
                is InferArg.ArgExpr -> {
                    val argExpr = inferArg.expr ?: continue
                    val argExprTy = argExpr.inferType(expected = expectation)
                    println("=== inferArgumentTypes ===")
                    println("argExpr.text: ${argExpr.text}")
                    println("argExprTy: $argExprTy")
                    println("expectedTy: $expectedTy")
                    println("formalInputTy: $formalInputTy")
                    println("hasExplicitTypeParameters: $hasExplicitTypeParameters")
                    val isTypeParameter = formalInputTy is TyTypeParameter || formalInputTy is TyInfer.TyVar || hasExplicitTypeParameters
                    println("isTypeParameter: $isTypeParameter")
                    println("ctx.msl: ${ctx.msl}")
                    if (argExprTy is TyInteger && expectedTy is TyInteger) {
                        println("isCompatibleIntegers result: ${isCompatibleIntegers(expectedTy, argExprTy, ctx.msl, isTypeParameter)}")
                    }
                    println("combine result: ${ctx.combineTypes(argExprTy, expectedTy, isTypeParameter)}")


                    if (!ctx.tryCoerce(argExprTy, expectedTy, isTypeParameter).isOk) {
                        ctx.reportTypeError(TypeError.TypeMismatch(argExpr, expectedTy, argExprTy))
                    }
                    // retrieve obligations
                    ctx.combineTypes(formalInputTy, expectedTy, isTypeParameter)
                }
                is InferArg.SelfType -> {
                    // method already resolved, so autoborrow() should always succeed
                    val actualSelfTy = autoborrow(inferArg.selfTy, expectedTy)
                        ?: error("unreachable, as method call cannot be resolved if autoborrow fails")
                    ctx.combineTypes(actualSelfTy, expectedTy)
                }
            }

            // retrieve obligations
            ctx.combineTypes(formalInputTy, expectedTy)
        }
    }

    fun inferMacroCallExprTy(macroExpr: MvAssertMacroExpr): Ty {
        val ident = macroExpr.identifier
        when (ident.text) {
            "assert" -> {
                val formalInputTys = listOf(TyBool, TyInteger.default())
                inferArgumentTypes(
                    formalInputTys,
                    emptyList(),
                    macroExpr.valueArguments.map { it.expr }.map { InferArg.ArgExpr(it) }
                )
            }
            "debug" -> {
                // debug! accepts any number and type of arguments
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
            }
            "transfer" -> {
                // transfer! typically accepts two arguments: object and address
                val formalInputTys = listOf(TyUnknown, TyAddress)
                inferArgumentTypes(
                    formalInputTys,
                    emptyList(),
                    macroExpr.valueArguments.map { it.expr }.map { InferArg.ArgExpr(it) }
                )
            }
            "event" -> {
                // event! accepts event type and arguments
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
            }
        }
        return TyUnit
    }

    fun inferMacroCallExprTy(macroExpr: MvMacroCallExpr): Ty {
        val pathText = macroExpr.path.text.removeSuffix("!")
        when (pathText) {
            "vector" -> {
                // vector! already has a dedicated handling method inferVectorLitExpr
                if (macroExpr.vectorLitItems != null) {
                    return TyUnknown // will be handled by dedicated method
                }
            }
            "option" -> {
                // option! accepts one argument
                if (macroExpr.valueArguments.size == 1) {
                    val argTy = macroExpr.valueArguments.first().expr?.inferType() ?: TyUnknown

                    // Try to resolve Option and build a TyAdt.
                    try {
                        val optionType = getOptionType()
                        if (optionType != null && argTy != TyUnknown) {
                            val typeArguments = listOf(argTy)
                            val substitution = Substitution(
                                optionType.typeParameters.withIndex().associate { (index, param) ->
                                    TyTypeParameter(param) to typeArguments.getOrElse(index) { TyUnknown }
                                }
                            )
                            return TyAdt(optionType, substitution, typeArguments)
                        }
                    } catch (e: Exception) {
                        // If Option cannot be resolved, fall back to TyUnknown.
                        println("Error getting option type: ${e.message}")
                    }
                }
                return TyUnknown // specific type needs to be inferred from argument
            }
            "result" -> {
                // result! accepts two arguments
                if (macroExpr.valueArguments.size == 2) {
                    val arg1Ty = macroExpr.valueArguments[0].expr?.inferType() ?: TyUnknown
                    val arg2Ty = macroExpr.valueArguments[1].expr?.inferType() ?: TyUnknown

                    // Try to resolve Result and build a TyAdt.
                    try {
                        val resultType = getResultType()
                        if (resultType != null && arg1Ty != TyUnknown && arg2Ty != TyUnknown) {
                            val typeArguments = listOf(arg1Ty, arg2Ty)
                            val substitution = Substitution(
                                resultType.typeParameters.withIndex().associate { (index, param) ->
                                    TyTypeParameter(param) to typeArguments.getOrElse(index) { TyUnknown }
                                }
                            )
                            return TyAdt(resultType, substitution, typeArguments)
                        }
                    } catch (e: Exception) {
                        // If Result cannot be resolved, fall back to TyUnknown.
                        println("Error getting result type: ${e.message}")
                    }
                }
                return TyUnknown // specific type needs to be inferred from arguments
            }
            "bcs" -> {
                // bcs! accepts one argument and returns byte vector
                if (macroExpr.valueArguments.size == 1) {
                    macroExpr.valueArguments.first().expr?.inferType()
                }
                return TyByteString(ctx.msl)
            }
            "object" -> {
                // object! is used for creating objects
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
                return TyUnknown
            }
            "table" -> {
                // table! is used for creating tables
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
                return TyUnknown
            }
            "system" -> {
                // system! system operations
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
                return TyUnit
            }
            "vote" -> {
                // vote! voting operations
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
                return TyUnit
            }
            "debug" -> {
                // debug! accepts any number and type of arguments
                macroExpr.valueArguments.forEach { it.expr?.inferType() }
                return TyUnit
            }
        }
        return TyUnknown
    }

    private fun getOptionType(): MvStructOrEnumItemElement? {
        // Try to resolve Option from pre-imported modules.
        val service = PreImportedModuleService.getInstance(project)
        val preImportedModules = service.getPreImportedModules()
        val optionModule = preImportedModules.find { it.name == "option" }
        val optionStruct = optionModule?.structs()?.firstOrNull { it.name == "Option" }
        if (optionStruct != null) {
            return optionStruct
        }

        // In tests the stdlib may be missing; create a dummy Option type.
        val psiFactory = project.psiFactory
        val dummyModule = psiFactory.inlineModule("std", "option", """
            struct Option<T> has copy, drop {
                vec: vector<u8>
            }
        """)
        return dummyModule.structs().firstOrNull()
    }

    private fun getResultType(): MvStructOrEnumItemElement? {
        // Try to resolve Result from pre-imported modules.
        val service = PreImportedModuleService.getInstance(project)
        val preImportedModules = service.getPreImportedModules()
        val resultModule = preImportedModules.find { it.name == "result" }
        val resultStruct = resultModule?.structs()?.firstOrNull { it.name == "Result" }
        if (resultStruct != null) {
            return resultStruct
        }

        // In tests the stdlib may be missing; create a dummy Result type.
        val psiFactory = project.psiFactory
        val dummyModule = psiFactory.inlineModule("std", "result", """
            struct Result<T, E> has copy, drop {
                vec: vector<u8>
            }
        """)
        return dummyModule.structs().firstOrNull()
    }

    /**
     * Unifies the output type with the expected type early, for more coercions
     * and forward type information on the input expressions
     */
    private fun expectedInputsForExpectedOutput(
        expectedRet: Expectation,
        formalRet: Ty,
        formalArgs: List<Ty>,
    ): List<Ty> {
        val resolvedFormalRet = resolveTypeVarsIfPossible(formalRet)
        val retTy = expectedRet.onlyHasTy(ctx) ?: return emptyList()
        // Rustc does `fudge` instead of `probe` here. But `fudge` seems useless in our simplified type inference
        // because we don't produce new type variables during unification
        // https://github.com/rust-lang/rust/blob/50cf76c24bf6f266ca6d253a/compiler/rustc_infer/src/infer/fudge.rs#L98
        return ctx.freezeUnification {
            if (ctx.combineTypes(retTy, resolvedFormalRet).isOk) {
                formalArgs.map { ctx.resolveTypeVarsIfPossible(it) }
            } else {
                emptyList()
            }
        }
    }

    fun inferAttrItem(attrItem: MvAttrItem) {
        val initializer = attrItem.attrItemInitializer
        if (initializer != null) initializer.expr?.let { inferExprTy(it) }
        for (innerAttrItem in attrItem.innerAttrItems) {
            inferAttrItem(innerAttrItem)
        }
    }

    @JvmName("inferType_")
    fun inferType(expr: MvExpr): Ty =
        expr.inferType()

    // combineTypes with errors
    fun coerce(element: PsiElement, inferred: Ty, expected: Ty): Boolean =
        coerceResolved(
            element,
            resolveTypeVarsIfPossible(inferred),
            resolveTypeVarsIfPossible(expected)
        )

    private fun coerceResolved(element: PsiElement, inferred: Ty, expected: Ty): Boolean {

        if (expected is TyUnit && element.parent is MvExprStmt) {

            return true
        }

        val coerceResult = ctx.tryCoerce(inferred, expected)
        return when (coerceResult) {
            is RsResult.Ok -> true
            is RsResult.Err -> when (val err = coerceResult.err) {
                is CombineTypeError.TypeMismatch -> {
                    checkTypeMismatch(err, element, inferred, expected)
                    false
                }
            }
        }
    }

    private fun inferDotExprTy(dotExpr: MvDotExpr, expected: Expectation): Ty {
        val receiverTy = ctx.resolveTypeVarsIfPossible(dotExpr.expr.inferType())

        val methodCall = dotExpr.methodCall
        val field = dotExpr.structDotField
        return when {
            methodCall != null -> inferMethodCallTy(receiverTy, methodCall, expected)
            field != null -> inferDotFieldTy(receiverTy, field)
            // incomplete
            else -> TyUnknown
        }
    }

    fun inferStructLitExprTy(litExpr: MvStructLitExpr, expected: Expectation): Ty {
        val path = litExpr.path
        val expectedType = expected.onlyHasTy(ctx)

        val item = resolvePathElement(litExpr, expectedType) as? MvFieldsOwner
        if (item == null) {
            for (field in litExpr.fields) {
                field.expr?.inferType()
            }
            return TyUnknown
        }

        val genericItem = if (item is MvEnumVariant) item.enumItem else (item as MvStruct)
        val (tyAdt, typeParameters) = ctx.instantiateMethodOrPath<TyAdt>(path, genericItem)
            ?: return TyUnknown
        expectedType?.let { expectedTy ->
            ctx.unifySubst(typeParameters, expectedTy.typeParameterValues)
        }

        litExpr.fields.forEach { field ->
            // todo: can be cached, change field reference impl
            val namedField =
                resolveSingleResolveVariant(field.referenceName) { it.processAll(NONE, item.namedFields) }
                        as? MvNamedFieldDecl
            val rawFieldTy = namedField?.type?.loweredType(msl)
            val fieldTy = rawFieldTy?.substitute(tyAdt.substitution) ?: TyUnknown
//            val fieldTy = field.type(msl)?.substitute(tyAdt.substitution) ?: TyUnknown
            val expr = field.expr
            if (expr != null) {
                expr.inferTypeCoercableTo(fieldTy)
            } else {
                val bindingTy = (resolveBindingForFieldShorthand(field).singleOrNull() as? MvPatBinding)
                    ?.let { ctx.getBindingType(it) }
                    ?: TyUnknown
//                val bindingTy = field.resolveToBinding()?.let { ctx.getBindingType(it) } ?: TyUnknown
                coerce(field, bindingTy, fieldTy)
            }
        }
        return tyAdt
    }

    private fun inferSchemaLitTy(schemaLit: MvSchemaLit): Ty {
        val path = schemaLit.path
        val schemaItem = path.maybeSchema
        if (schemaItem == null) {
            for (field in schemaLit.fields) {
                field.expr?.inferType()
            }
            return TyUnknown
        }

        val (schemaTy, _) = ctx.instantiateMethodOrPath<TySchema>(path, schemaItem) ?: return TyUnknown
//        expected.onlyHasTy(ctx)?.let { expectedTy ->
//            ctx.unifySubst(typeParameters, expectedTy.typeParameterValues)
//        }

        schemaLit.fields.forEach { field ->
            val fieldTy = field.type(msl)?.substitute(schemaTy.substitution) ?: TyUnknown
            val expr = field.expr

            if (expr != null) {
                expr.inferTypeCoercableTo(fieldTy)
            } else {
                val bindingTy = field.resolveToBinding()?.let { ctx.getBindingType(it) } ?: TyUnknown
                coerce(field, bindingTy, fieldTy)
            }
        }
        return schemaTy
    }

    private fun MvSchemaLitField.type(msl: Boolean) =
        this.resolveToDeclaration()?.type?.loweredType(msl)

//    private fun MvStructLitField.type(msl: Boolean) =
//        this.resolveToDeclaration()?.type?.loweredType(msl)

    fun inferVectorLitExpr(litExpr: MvVectorLitExpr, expected: Expectation): Ty {
        println("=== inferVectorLitExpr ===")
        println("litExpr.text: ${litExpr.text}")
        println("litExpr.typeArgument: ${litExpr.typeArgument}")

        val tyVar = TyInfer.TyVar()
        val explicitTy = litExpr.typeArgument?.type?.loweredType(msl)
        if (explicitTy != null) {
            println("explicitTy: ${explicitTy}")
            ctx.combineTypes(tyVar, explicitTy)
        }

        val exprs = litExpr.vectorLitItems.exprList
        println("exprs.size: ${exprs.size}")
        val formalInputs = generateSequence { ctx.resolveTypeVarsIfPossible(tyVar) }.take(exprs.size).toList()
        val expectedInputTys =
            expectedInputsForExpectedOutput(expected, TyVector(tyVar), formalInputs)
        inferArgumentTypes(
            formalInputs,
            expectedInputTys,
            exprs.map { InferArg.ArgExpr(it) }
        )


        if (explicitTy != null) {
            for (expr in exprs) {
                val exprTy = ctx.getExprType(expr)
                println("expr.text: ${expr.text}, exprTy: ${exprTy}")
                if (exprTy is TyInteger && explicitTy is TyInteger) {
                    if (exprTy.kind != explicitTy.kind) {

                        ctx.reportTypeError(TypeError.TypeMismatch(expr, explicitTy, exprTy))
                    }
                } else if (!ctx.tryCoerce(exprTy, explicitTy).isOk) {
                    ctx.reportTypeError(TypeError.TypeMismatch(expr, explicitTy, exprTy))
                }
            }
        } else {

            if (exprs.size > 1) {
                val firstExprTy = ctx.getExprType(exprs[0])
                for (i in 1 until exprs.size) {
                    val expr = exprs[i]
                    val exprTy = ctx.getExprType(expr)
                    if (firstExprTy is TyInteger && exprTy is TyInteger) {
                        if (firstExprTy.kind != exprTy.kind) {

                            ctx.reportTypeError(TypeError.TypeMismatch(expr, firstExprTy, exprTy))
                        }
                    } else if (!ctx.tryCoerce(exprTy, firstExprTy).isOk) {
                        ctx.reportTypeError(TypeError.TypeMismatch(expr, firstExprTy, exprTy))
                    }
                }
            }


            var hasIntegerElements = false
            for (expr in exprs) {
                val exprTy = ctx.getExprType(expr)
                if (exprTy is TyInteger) {
                    hasIntegerElements = true
                    break
                }
            }
            if (hasIntegerElements) {

                var hasExplicitTypeElement = false
                for (vectorElement in exprs) {
                    val vectorElementText = vectorElement.text
                    if (vectorElementText.contains("u") || vectorElementText.contains("i")) {
                        hasExplicitTypeElement = true
                        break
                    }
                }
                if (!hasExplicitTypeElement) {

                    ctx.combineTypes(tyVar, TyInteger.default())
                }
            }
        }


        val resolvedTyVar = ctx.resolveTypeVarsIfPossible(tyVar)
        if (resolvedTyVar is TyInfer.TyVar && !ctx.msl) {
            ctx.combineTypes(tyVar, TyInteger.default())
        }

        val vectorTy = ctx.resolveTypeVarsIfPossible(TyVector(tyVar))
        println("vectorTy: ${vectorTy}")
        println("=== inferVectorLitExpr end ===")
        return vectorTy
    }

    private fun inferIndexExprTy(indexExpr: MvIndexExpr): Ty {
        val receiverTy = indexExpr.receiverExpr.inferType()
        val argTy = indexExpr.argExpr.inferType()

        // compiler v2 only in non-msl
        if (!ctx.msl && !project.moveSettings.enableIndexExpr) {
            return TyUnknown
        }

        val derefTy = receiverTy.derefIfNeeded()
        return when {
            derefTy is TyVector -> {
                // argExpr can be either TyInteger or TyRange
                when (argTy) {
                    is TyRange -> derefTy
                    is TyInteger, is TyInfer.IntVar, is TyNum -> derefTy.item
                    else -> {
                        coerce(indexExpr.argExpr, argTy, if (ctx.msl) TyNum else TyInteger.DEFAULT)
                        TyUnknown
                    }
                }
            }
            receiverTy is TyAdt -> {
                coerce(indexExpr.argExpr, argTy, TyAddress)
                receiverTy
            }
            else -> {
                ctx.reportTypeError(TypeError.IndexingIsNotAllowed(indexExpr.receiverExpr, receiverTy))
                TyUnknown
            }
        }
    }

    private fun inferQuantExprTy(quantExpr: MvQuantExpr): Ty {
        quantExpr.quantBindings?.quantBindingList.orEmpty()
            .forEach {
                collectQuantBinding(it)
            }
        quantExpr.quantWhere?.expr?.inferTypeCoercableTo(TyBool)
        quantExpr.expr?.inferTypeCoercableTo(TyBool)
        return TyBool
    }

    private fun collectQuantBinding(quantBinding: MvQuantBinding) {
        val bindingPat = quantBinding.binding
        val ty = when (quantBinding) {
            is MvRangeQuantBinding -> {
                val rangeTy = quantBinding.expr?.inferType()
                when (rangeTy) {
                    is TyVector -> rangeTy.item
                    is TyRange -> TyInteger.DEFAULT
                    else -> TyUnknown
                }
            }
            is MvTypeQuantBinding -> quantBinding.type?.loweredType(true) ?: TyUnknown
            else -> error("unreachable")
        }
        this.ctx.writePatTy(bindingPat, ty)
    }

    private fun inferRangeExprTy(rangeExpr: MvRangeExpr): Ty {
        val leftTy = rangeExpr.exprList.firstOrNull()?.inferType() ?: TyUnknown
//        rangeExpr.exprList.firstOrNull()?.inferTypeCoercableTo(TyInteger.DEFAULT)
        rangeExpr.exprList.drop(1).firstOrNull()?.inferType(expected = leftTy)
//        rangeExpr.exprList.drop(1).firstOrNull()?.inferTypeCoercableTo(TyInteger.DEFAULT)
        return TyRange(leftTy)
    }

    private fun inferBinaryExprTy(binaryExpr: MvBinaryExpr): Ty {
        return when (binaryExpr.binaryOp.op) {
            "<", ">", "<=", ">=" -> inferOrderingBinaryExprTy(binaryExpr)
            "+", "-", "*", "/", "%" -> inferArithmeticBinaryExprTy(binaryExpr)
            "==", "!=" -> inferEqualityBinaryExprTy(binaryExpr)
            "||", "&&", "==>", "<==>" -> inferLogicBinaryExprTy(binaryExpr)
            "^", "|", "&" -> inferBitOpsExprTy(binaryExpr)
            "<<", ">>" -> inferBitShiftsExprTy(binaryExpr)
            else -> TyUnknown
        }
    }

    private fun inferArithmeticBinaryExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right
        val op = binaryExpr.binaryOp.op

        var typeErrorEncountered = false
        val leftTy = leftExpr.inferType()
        ctx.writeExprTy(leftExpr, leftTy)
        if (!leftTy.supportsArithmeticOp()) {
            ctx.reportTypeError(TypeError.UnsupportedBinaryOp(leftExpr, leftTy, op))
            typeErrorEncountered = true
        }
        if (rightExpr != null) {
            val rightTy = rightExpr.inferType()
            ctx.writeExprTy(rightExpr, rightTy)
            if (!rightTy.supportsArithmeticOp()) {
                ctx.reportTypeError(TypeError.UnsupportedBinaryOp(rightExpr, rightTy, op))
                typeErrorEncountered = true
            }

            if (!typeErrorEncountered && ctx.combineTypes(leftTy, rightTy).isErr) {
                ctx.reportTypeError(
                    TypeError.IncompatibleArgumentsToBinaryExpr(
                        binaryExpr,
                        leftTy,
                        rightTy,
                        op
                    )
                )
                typeErrorEncountered = true
            }


            if (!typeErrorEncountered) {
                // Integer inference: prefer the suffixed kind over the default kind.
                if (leftTy is TyInteger && rightTy is TyInteger) {
                    // If left is default and right is concrete, choose right.
                    if (leftTy.kind == TyInteger.DEFAULT_KIND && rightTy.kind != TyInteger.DEFAULT_KIND) {
                        coerce(leftExpr, leftTy, expected = rightTy)
                        return rightTy
                    }
                    // If right is default and left is concrete, choose left.
                    if (rightTy.kind == TyInteger.DEFAULT_KIND && leftTy.kind != TyInteger.DEFAULT_KIND) {
                        coerce(rightExpr, rightTy, expected = leftTy)
                        return leftTy
                    }
                }
            }
        }
        return if (typeErrorEncountered) TyUnknown else leftTy
    }

    private fun inferEqualityBinaryExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right
        val op = binaryExpr.binaryOp.op

        val leftTy = ctx.resolveTypeVarsIfPossible(leftExpr.inferType())
        if (rightExpr != null) {
            val rightTy = ctx.resolveTypeVarsIfPossible(rightExpr.inferType())

            // if any of the types has TyUnknown and TyInfer, combineTyVar will fail
            // it only happens in buggy situation, but it's annoying for the users, so return if not in devMode
            if (!isDebugModeEnabled()) {
                if ((leftTy.hasTyUnknown || rightTy.hasTyUnknown)
                    && (leftTy.hasTyInfer || rightTy.hasTyInfer)
                ) {
                    return TyBool
                }
            }


            if (ctx.msl) {
                if (leftTy is TyNum || rightTy is TyNum || leftTy is TyInteger || rightTy is TyInteger) {
                    return TyBool
                }
            }

            if (leftTy is TyInteger && rightTy is TyInteger) {
                if (leftTy.kind != rightTy.kind) {

                    ctx.reportTypeError(
                        TypeError.IncompatibleArgumentsToBinaryExpr(binaryExpr, leftTy, rightTy, op)
                    )
                }
            } else if (ctx.combineTypes(leftTy, rightTy).isErr) {
                ctx.reportTypeError(
                    TypeError.IncompatibleArgumentsToBinaryExpr(binaryExpr, leftTy, rightTy, op)
                )
            }
        }
        return TyBool
    }

    private fun inferOrderingBinaryExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right
        val op = binaryExpr.binaryOp.op

        var typeErrorEncountered = false
        val leftTy = leftExpr.inferType()
        if (!leftTy.supportsOrdering()) {
            ctx.reportTypeError(TypeError.UnsupportedBinaryOp(leftExpr, leftTy, op))
            typeErrorEncountered = true
        }
        if (rightExpr != null) {
            val rightTy = rightExpr.inferType()
            if (!rightTy.supportsOrdering()) {
                ctx.reportTypeError(TypeError.UnsupportedBinaryOp(rightExpr, rightTy, op))
                typeErrorEncountered = true
            }


            println("=== inferOrderingBinaryExprTy ===")
            println("binaryExpr.text: ${binaryExpr.text}")
            println("ctx.msl: ${ctx.msl}")
            println("binaryExpr.isMsl(): ${binaryExpr.isMsl()}")
            println("leftTy: $leftTy, leftTy.class: ${leftTy.javaClass.simpleName}")
            println("rightTy: $rightTy, rightTy.class: ${rightTy.javaClass.simpleName}")


            if (ctx.msl || binaryExpr.isMsl()) {
                return TyBool
            }


            var hasSpecAncestor = false
            var current = binaryExpr.parent
            while (current != null) {
                if (current is MvWhileExpr) {

                    current.accept(object : PsiRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) {
                            if (element is MvSpecCodeBlock) {
                                hasSpecAncestor = true
                            }
                            super.visitElement(element)
                        }
                    })
                    break
                }
                current = current.parent
            }

            if (hasSpecAncestor && leftTy is TyInteger && rightTy is TyInteger) {
                return TyBool
            }


            println("=== inferOrderingBinaryExprTy ===")
            println("binaryExpr.text: ${binaryExpr.text}")
            println("ctx.msl: ${ctx.msl}")
            println("leftTy: $leftTy, leftTy.class: ${leftTy.javaClass.simpleName}")
            println("rightTy: $rightTy, rightTy.class: ${rightTy.javaClass.simpleName}")
            if (!typeErrorEncountered && !ctx.msl) {
                if (leftTy is TyInteger && rightTy is TyInteger) {
                    if (leftTy.kind != rightTy.kind) {

                        ctx.reportTypeError(TypeError.TypeMismatch(rightExpr, leftTy, rightTy))
                        typeErrorEncountered = true
                    }
                } else {
                    coerce(rightExpr, rightTy, expected = leftTy)
                }
            }
        }
        return TyBool
    }

    private fun inferLogicBinaryExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right

        leftExpr.inferTypeCoercableTo(TyBool)
        if (rightExpr != null) {
            rightExpr.inferTypeCoercableTo(TyBool)
        }
        return TyBool
    }

    private fun inferBitOpsExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right

        val leftTy = leftExpr.inferTypeCoercableTo(TyInteger.DEFAULT)
        if (rightExpr != null) {
            rightExpr.inferTypeCoercableTo(leftTy)
        }
        return leftTy
    }

    private fun inferBitShiftsExprTy(binaryExpr: MvBinaryExpr): Ty {
        val leftExpr = binaryExpr.left
        val rightExpr = binaryExpr.right

        leftExpr.inferTypeCoercableTo(TyInteger.DEFAULT)
        if (rightExpr != null) {

            if (ctx.msl || binaryExpr.isMsl()) {
                val rightTy = rightExpr.inferType()
                if (rightTy !is TyInteger && rightTy !is TyNum) {
                    ctx.reportTypeError(TypeError.TypeMismatch(rightExpr, TyInteger.U8, rightTy))
                }
            } else {

                val rightTy = rightExpr.inferType()
                if (rightTy !is TyInteger) {
                    ctx.reportTypeError(TypeError.TypeMismatch(rightExpr, TyInteger.U8, rightTy))
                } else {

                    val isUnsuffixedIntegerLiteral = rightExpr is MvLitExpr &&
                        (rightExpr.integerLiteral != null || rightExpr.hexIntegerLiteral != null) &&
                        !rightExpr.text.contains("u") && !rightExpr.text.contains("i")

                    if (!isUnsuffixedIntegerLiteral && rightTy != TyInteger.U8) {
                        ctx.reportTypeError(TypeError.TypeMismatch(rightExpr, TyInteger.U8, rightTy))
                    }
                }
            }
        }
        return TyInteger.U64
    }

    private fun Ty.supportsArithmeticOp(): Boolean {
        val ty = this
//        val ty = resolveTypeVarsWithObligations(this)
        return ty is TyInteger
                || ty is TyNum
                || ty is TyInfer.TyVar
                || ty is TyInfer.IntVar
                || ty is TyUnknown
                || ty is TyNever
    }

    private fun Ty.supportsOrdering(): Boolean {
        val ty = resolveTypeVarsIfPossible(this)
        return ty is TyInteger
                || ty is TyNum
                || ty is TyInfer.IntVar
                || ty is TyUnknown
                || ty is TyNever
    }

    private fun inferDerefExprTy(derefExpr: MvDerefExpr): Ty {
        val innerExpr = derefExpr.expr ?: return TyUnknown
        val innerExprTy = innerExpr.inferType()
        if (innerExprTy !is TyReference) {
            ctx.reportTypeError(TypeError.InvalidDereference(innerExpr, innerExprTy))
            return TyUnknown
        }
        return innerExprTy.referenced
    }

    private fun inferTupleLitExprTy(tupleExpr: MvTupleLitExpr, expected: Expectation): Ty {
        val types = tupleExpr.exprList.mapIndexed { i, itemExpr ->
            val expectedTy = (expected.onlyHasTy(ctx) as? TyTuple)?.types?.getOrNull(i)
            if (expectedTy != null) {
                itemExpr.inferTypeCoerceTo(expectedTy)
            } else {
                itemExpr.inferType()
            }
        }
        return TyTuple(types)
    }

    private fun inferLitExprTy(litExpr: MvLitExpr, expected: Expectation): Ty {
        println("inferLitExprTy called for: ${litExpr.text}")
        println("  ctx.msl: ${ctx.msl}")
        println("  integerLiteral: ${litExpr.integerLiteral != null}")
        println("  hexIntegerLiteral: ${litExpr.hexIntegerLiteral != null}")

        if (ctx.msl && (litExpr.integerLiteral != null || litExpr.hexIntegerLiteral != null)) {
            println("  Returning TyNum")
            return TyNum
        }


        var isInsideVectorLit = false

        var isInsideFunctionCallArg = false
        var current: PsiElement? = litExpr
        while (current != null) {
            if (current is MvVectorLitExpr) {
                isInsideVectorLit = true
                break
            }
            if (current is MvCallExpr) {
                isInsideFunctionCallArg = true

                val target = current.reference?.resolve()
                if (target is MvFunctionLike && target.typeParameters.isNotEmpty()) {
                    isInsideVectorLit = true
                }
                break
            }
            current = current.parent
        }

        val litTy =
            when {
                litExpr.boolLiteral != null -> TyBool
                litExpr.addressLit != null -> TyAddress
                litExpr.integerLiteral != null || litExpr.hexIntegerLiteral != null -> {
                    val literal = (litExpr.integerLiteral ?: litExpr.hexIntegerLiteral)!!
                    val fromLiteral = TyInteger.fromSuffixedLiteral(literal)
                    if (fromLiteral == null && isInsideVectorLit) {

                        TyInteger.default()
                    } else {

                        fromLiteral ?: TyInteger.U64
                    }
                }
                litExpr.byteStringLiteral != null -> TyByteString(ctx.msl)
                litExpr.hexStringLiteral != null -> TyHexString(ctx.msl)
                else -> TyUnknown
            }

        val expectedTy = expected.onlyHasTy(this.ctx)
        if (expectedTy != null) {
            coerce(litExpr, litTy, expectedTy)

            // For unsuffixed integers, only return the expected integer type when it is explicit.
            if (!ctx.msl && (litExpr.integerLiteral != null || litExpr.hexIntegerLiteral != null)) {
                val literal = (litExpr.integerLiteral ?: litExpr.hexIntegerLiteral)!!
                if (TyInteger.fromSuffixedLiteral(literal) == null && expectedTy is TyInteger) {
                    return if (expectedTy != TyInteger.default()) expectedTy else TyInteger.default()
                }
            }
        }

        return litTy
    }

    private fun isGenericContext(element: PsiElement): Boolean {

        var current: PsiElement? = element
        while (current != null) {

            if (current is MvCallExpr) {

                val target = current.reference?.resolve()
                if (target is MvFunctionLike && target.typeParameters.isNotEmpty()) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun inferIfExprTy(ifExpr: MvIfExpr, expected: Expectation): Ty {
        ifExpr.condition?.expr?.inferTypeCoercableTo(TyBool)

        println("=== inferIfExprTy ===")
        println("ifExpr.text: ${ifExpr.text}")
        println("ifExpr.class: ${ifExpr.javaClass.simpleName}")
        println("ifExpr.codeBlock: ${ifExpr.codeBlock?.text}")
        println("ifExpr.codeBlock?.expr: ${ifExpr.codeBlock?.expr?.text}")
        println("ifExpr.inlineBlock: ${ifExpr.inlineBlock?.text}")
        println("ifExpr.inlineBlock?.expr: ${ifExpr.inlineBlock?.expr?.text}")


        var isInsideExprStmt = false
        var isInsideAssignment = false
        var current: PsiElement? = ifExpr
        while (current != null) {
            if (current is MvExprStmt) {
                isInsideExprStmt = true
                break
            }
            if (current is MvAssignmentExpr) {
                isInsideAssignment = true
                break
            }
            current = current.parent
        }

        val expectedTy = expected.onlyHasTy(ctx)
        val actualIfTy =
            ifExpr.codeBlock?.inferBlockType(expected, coerce = true)
                ?: ifExpr.inlineBlock?.expr?.inferTypeCoercableTo(expected)
        val elseBlock = ifExpr.elseBlock ?: return TyUnknown
        val actualElseTy =
            elseBlock.codeBlock?.inferBlockType(expected, coerce = true)
                ?: elseBlock.inlineBlock?.expr?.inferTypeCoercableTo(expected)

        println("elseBlock.text: ${elseBlock.text}")
        println("elseBlock.class: ${elseBlock.javaClass.simpleName}")
        println("elseBlock.codeBlock: ${elseBlock.codeBlock?.text}")
        println("elseBlock.codeBlock?.expr: ${elseBlock.codeBlock?.expr?.text}")
        println("elseBlock.inlineBlock: ${elseBlock.inlineBlock?.text}")
        println("elseBlock.inlineBlock?.expr: ${elseBlock.inlineBlock?.expr?.text}")
        println("elseBlock.tailExpr: ${elseBlock.tailExpr?.text}")


        if (expectedTy != null) {
            val ifExprElement = ifExpr.codeBlock?.expr ?: ifExpr.inlineBlock?.expr
            if (ifExprElement != null && actualIfTy != null && !ctx.tryCoerce(actualIfTy, expectedTy).isOk) {
                ctx.reportTypeError(TypeError.TypeMismatch(ifExprElement, expectedTy, actualIfTy))
            }
            val elseExprElement = elseBlock.codeBlock?.expr ?: elseBlock.inlineBlock?.expr
            if (elseExprElement != null && actualElseTy != null && !ctx.tryCoerce(actualElseTy, expectedTy).isOk) {
                ctx.reportTypeError(TypeError.TypeMismatch(elseExprElement, expectedTy, actualElseTy))
            }
        }

        val expectedElseTy = expectedTy ?: actualIfTy ?: TyUnknown
        if (actualElseTy != null) {

            val elseExpr = elseBlock.inlineBlock?.expr ?: elseBlock.codeBlock?.expr
            if (elseExpr != null) {
                if (expectedElseTy is TyReference && actualElseTy is TyReference) {
                    coerce(elseExpr, actualElseTy.referenced, expectedElseTy.referenced)
                } else {
                    if (actualIfTy != null && !ctx.tryCoerce(actualElseTy, actualIfTy).isOk) {
                        println("=== Type mismatch found ===")
                        println("elseExpr.text: ${elseExpr.text}")
                        println("actualIfTy: $actualIfTy")
                        println("actualElseTy: $actualElseTy")
                        ctx.reportTypeError(TypeError.TypeMismatch(elseExpr, actualIfTy, actualElseTy))
                    } else if (!ctx.tryCoerce(actualElseTy, expectedElseTy).isOk) {
                        println("=== Type mismatch with expected ===")
                        println("elseExpr.text: ${elseExpr.text}")
                        println("expectedElseTy: $expectedElseTy")
                        println("actualElseTy: $actualElseTy")
                        ctx.reportTypeError(TypeError.TypeMismatch(elseExpr, expectedElseTy, actualElseTy))
                    }
                }
            }
        }


        val resultTy = if (actualIfTy != null && actualElseTy != null) {
            if (ctx.tryCoerce(actualIfTy, actualElseTy).isOk) {
                actualElseTy
            } else if (ctx.tryCoerce(actualElseTy, actualIfTy).isOk) {
                actualIfTy
            } else {

                TyUnknown
            }
        } else {
            TyUnknown
        }


        val finalResultTy = if (isInsideExprStmt && !isInsideAssignment) TyUnit else resultTy
        ctx.writeExprTy(ifExpr, finalResultTy)
        return finalResultTy
    }

    private fun isFunctionReturnContext(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is MvReturnExpr) {
                return true
            }
            if (current is MvFunction) {

                val functionBody = current.codeBlock
                if (functionBody != null) {
                    val statements = functionBody.stmtList
                    if (statements.isNotEmpty()) {
                        val lastStatement = statements.last()
                        if (lastStatement is MvExprStmt && lastStatement.expr == element) {
                            return true
                        }
                    }
                }
            }
            current = current.parent
        }
        return false
    }

    private fun intersectTypes(types: List<Ty>, symmetric: Boolean = true, element: PsiElement? = null): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> intersectTypes(acc, ty, symmetric, element) }
    }

    private fun intersectTypes(ty1: Ty, ty2: Ty, symmetric: Boolean = true, element: PsiElement? = null): Ty {
        return when {
            ty1 is TyNever -> ty2
            ty2 is TyNever -> ty1
            ty1 is TyUnknown -> if (ty2 !is TyNever) ty2 else TyUnknown
            else -> {
                val ok = ctx.combineTypes(ty1, ty2).isOk
                        || if (symmetric) ctx.combineTypes(ty2, ty1).isOk else false
                if (ok) {
                    when {
                        ty1 is TyReference && ty2 is TyReference -> {
                            val minimalMut = ty1.mutability.intersect(ty2.mutability)
//                            val combined = ty1.permissions.intersect(ty2.permissions)
                            TyReference(ty1.referenced, minimalMut, ty1.msl || ty2.msl)
                        }
                        else -> ty1
                    }
                } else {

                    if (element != null) {
                        ctx.reportTypeError(TypeError.TypeMismatch(element, ty1, ty2))
                    }
                    TyUnknown
                }
            }
        }
    }

    private fun inferWhileExpr(whileExpr: MvWhileExpr): Ty {
        val condition = whileExpr.condition
        val conditionExpr = condition?.expr
        if (conditionExpr != null) {
            if (!ctx.isTypeInferred(conditionExpr)) {
                val ty = conditionExpr.inferTypeCoercableTo(TyBool)
                ctx.writeExprTy(conditionExpr, ty)
            }
        }

        // Ensure the while expression itself is typed.
        if (!ctx.isTypeInferred(whileExpr)) {
            ctx.writeExprTy(whileExpr, TyNever)
        }
        return inferLoopLikeBlock(whileExpr)
    }

    private fun inferLoopExpr(loopExpr: MvLoopExpr): Ty {
        return inferLoopLikeBlock(loopExpr)
    }

    private fun inferForExpr(forExpr: MvForExpr): Ty {
        val iterCondition = forExpr.forIterCondition
        if (iterCondition != null) {
            val rangeExpr = iterCondition.expr
            if (rangeExpr != null) {
                val rangeTy = rangeExpr.inferType()
                ctx.writeExprTy(rangeExpr, rangeTy)
                val bindingTy = (rangeTy as? TyRange)?.item ?: TyUnknown
                val bindingPat = iterCondition.patBinding
                if (bindingPat != null) {
                    this.ctx.writePatTy(bindingPat, bindingTy)
                }
            }
        }
        return inferLoopLikeBlock(forExpr)
    }

    private fun inferLoopLikeBlock(loopLike: MvLoopLike): Ty {
        // Infer loop condition types.
        when (loopLike) {
            is MvWhileExpr -> {
                val condition = loopLike.condition
                val expr = condition?.expr
                if (expr != null) {
                    if (!ctx.isTypeInferred(expr)) {
                        val ty = expr.inferTypeCoercableTo(TyBool)
                        ctx.writeExprTy(expr, ty)
                    }
                }

                // Type the while expression itself.
                if (loopLike is MvExpr && !ctx.isTypeInferred(loopLike)) {
                    ctx.writeExprTy(loopLike, TyNever)
                }
            }
            is MvForExpr -> {
                val forIterCondition = loopLike.forIterCondition
                val expr = forIterCondition?.expr
                if (expr != null) {
                    if (!ctx.isTypeInferred(expr)) {
                        val ty = expr.inferType()
                        ctx.writeExprTy(expr, ty)
                    }
                }

                // Type the for expression itself.
                if (loopLike is MvExpr && !ctx.isTypeInferred(loopLike)) {
                    ctx.writeExprTy(loopLike, TyNever)
                }
            }
            is MvLoopExpr -> {
                // Type the loop expression itself.
                if (loopLike is MvExpr && !ctx.isTypeInferred(loopLike)) {
                    ctx.writeExprTy(loopLike, TyNever)
                }
            }
        }

        val codeBlock = loopLike.codeBlock
        val inlineBlockExpr = loopLike.inlineBlock?.expr
        val expected = Expectation.maybeHasType(TyUnit)
        when {
            codeBlock != null -> codeBlock.inferBlockType(expected)
            inlineBlockExpr != null -> inlineBlockExpr.inferType(expected)
        }

        return TyNever
    }

    private fun inferMatchExprTy(matchExpr: MvMatchExpr): Ty {
        val matchingTy = ctx.resolveTypeVarsIfPossible(matchExpr.matchArgument.expr?.inferType() ?: TyUnknown)
        val arms = matchExpr.arms
        for (arm in arms) {
            (arm.pat as? MvPat)?.extractBindings(this, matchingTy)
            // For PathExpr, we might need to process its bindings
            if (arm.pat is MvPathExpr) {
                // Currently PathExpr might not have bindings, but we need to handle this case
                // We might need to add code to process PathExpr's bindings
            }
            arm.expr?.inferType()
            (arm.matchArmGuard as MvMatchArmGuard?)?.getExpr()?.inferType(TyBool)
        }
        return intersectTypes(arms.mapNotNull { it.expr?.let(ctx::getExprType) })
    }

    private fun inferIncludeStmt(includeStmt: MvIncludeStmt) {
        val includeItem = includeStmt.includeItem ?: return
        when (includeItem) {
            is MvSchemaIncludeItem -> inferSchemaLitTy(includeItem.schemaLit)
            is MvAndIncludeItem -> {
                includeItem.schemaLitList.forEach { inferSchemaLitTy(it) }
            }
            is MvIfElseIncludeItem -> {
                com.intellij.psi.util.PsiTreeUtil.getChildOfType(includeItem.getCondition(), MvExpr::class.java)?.inferTypeCoercableTo(TyBool)
                includeItem.schemaLitList.forEach { inferSchemaLitTy(it) }
            }
            is MvImplyIncludeItem -> {
                com.intellij.psi.util.PsiTreeUtil.getChildOfType(includeItem, MvExpr::class.java)?.inferTypeCoercableTo(TyBool)
                inferSchemaLitTy(includeItem.schemaLit)
            }
            else -> error("unreachable")
        }
    }

    private fun inferUpdateStmt(updateStmt: MvUpdateSpecStmt) {
        updateStmt.exprList.forEach {
            val ty = it.inferType()
            ctx.writeExprTy(it, ty)
        }
    }

    private fun MvPat.extractBindings(ty: Ty) {
        this.extractBindings(this@TypeInferenceWalker, ty)
    }

    private fun checkTypeMismatch(
        result: CombineTypeError.TypeMismatch,
        element: PsiElement,
        inferred: Ty,
        expected: Ty
    ) {
        if (result.ty1.javaClass in IGNORED_TYS || result.ty2.javaClass in IGNORED_TYS) return

        if (expected is TyReference && inferred is TyReference &&
            (expected.containsTyOfClass(listOf(TyUnknown::class.java, TyInfer.TyVar::class.java)) ||
             inferred.containsTyOfClass(listOf(TyUnknown::class.java, TyInfer.TyVar::class.java)))
        ) {
            // report errors with unknown types when &mut is needed, but & is present
            if (!(expected.mutability.isMut && !inferred.mutability.isMut)) {
                return
            }
        }
        reportTypeMismatch(element, expected, inferred)
    }

    private fun reportTypeMismatch(element: PsiElement, expected: Ty, inferred: Ty) {
        reportTypeError(TypeError.TypeMismatch(element, expected, inferred))
    }

    fun reportTypeError(typeError: TypeError) = ctx.reportTypeError(typeError)

    companion object {
        // ignoring possible false-positives (it's only basic experimental type checking)

        val IGNORED_TYS: List<Class<out Ty>> = listOf(
            TyUnknown::class.java,
            TyInfer.TyVar::class.java,
//            TyTypeParameter::class.java,
        )
    }
}
