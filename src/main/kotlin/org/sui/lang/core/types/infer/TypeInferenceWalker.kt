package org.sui.lang.core.types.infer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.sui.cli.settings.debugErrorOrFallback
import org.sui.cli.settings.isDebugModeEnabled
import org.sui.cli.settings.moveLanguageFeatures
import org.sui.ide.formatter.impl.location
import org.sui.lang.MvElementTypes
import org.sui.lang.core.macros.DefaultMacroSemanticService
import org.sui.lang.core.macros.MacroSemanticService
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.*
import org.sui.lang.moveProject
import org.sui.lang.core.resolve.collectMethodOrPathResolveVariants
import org.sui.lang.core.resolve.collectResolveVariantsAsScopeEntries
import org.sui.lang.core.resolve.isVisibleFrom
import org.sui.lang.core.resolve.processAll
import org.sui.lang.core.resolve.ref.FUNCTIONS
import org.sui.lang.core.resolve.ref.ITEM_NAMESPACES
import org.sui.lang.core.resolve.ref.NONE
import org.sui.lang.core.resolve.resolveSingleResolveVariant
import org.sui.lang.core.resolve2.PathKind
import org.sui.lang.core.resolve2.processMethodResolveVariants
import org.sui.lang.core.resolve2.processNestedScopesUpwards
import org.sui.lang.core.resolve2.ref.InferenceCachedPathElement
import org.sui.lang.core.resolve2.ref.ResolutionContext
import org.sui.lang.core.resolve2.ref.processPathResolveVariantsWithExpectedType
import org.sui.lang.core.resolve2.ref.resolveAliases
import org.sui.lang.core.resolve2.ref.resolvePathRaw
import org.sui.lang.core.resolve2.resolveBindingForFieldShorthand
import org.sui.lang.core.types.infer.deepFoldTyTypeParameterWith
import org.sui.lang.core.types.ty.*
import org.sui.lang.core.types.ty.TyReference.Companion.autoborrow
import org.sui.stdext.RsResult
import org.sui.stdext.chain

class TypeInferenceWalker(
    val ctx: InferenceContext,
    val project: Project,
    private val returnTy: Ty
) {
    private val specBindingNamesCache = mutableMapOf<MvCodeBlock, Set<String>>()
    private val macroSemanticService: MacroSemanticService = DefaultMacroSemanticService

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

        val tailExpr = this.expr
        val tailExprIsStmt = tailExpr?.parent is MvExprStmt
        val tailExprForType = if (tailExprIsStmt) null else tailExpr
        val expectedTy = expected.onlyHasTy(ctx)

        stmts.forEach { stmt ->
            processStatement(stmt)
        }

        // Process all types of expressions
        // These expressions may exist directly as statements in the PSI tree without being wrapped in ExprStmt
        // We need to find these expressions and perform type inference on them
        // However, we need to avoid reprocessing already processed expressions, especially MvAbortExpr
        this.descendantsOfType<MvExpr>().forEach { expr ->
            if (coerce && expectedTy != null && tailExprForType != null && tailExprForType.contains(expr)) {
                return@forEach
            }
            if (!ctx.isTypeInferred(expr) && expr !is MvAbortExpr) {
                expr.inferType()
            }
        }

        // If the block contains statements like return, break, continue, or abort, return TyNever
        if (hasNeverTypeStatement(stmts)) {
            return TyNever
        }

        return if (tailExprForType == null) {
            // No tail expression: the block evaluates to TyUnit.
            if (coerce && expectedTy != null) {
                coerce(this.rBrace ?: this, TyUnit, expectedTy)
            }
            TyUnit
        } else {
            // Handle tail expression
            val ty =
                if (coerce && expectedTy != null) {
                    tailExprForType.inferType(Expectation.maybeHasType(expectedTy))
                } else {
                    tailExprForType.inferType()
                }

            // Ensure the type of the tail expression is correctly written to the context
            // If it's an MvAbortExpr type, we should preserve TyNever and not overwrite it
            if (tailExprForType is MvAbortExpr) {
                val neverTy = TyNever
                ctx.writeExprTy(tailExprForType, neverTy)
                return neverTy
            } else {

                var actualTy = ctx.getExprType(tailExprForType) ?: ty

                if (coerce
                    && expectedTy is TyInteger
                    && expectedTy.kind != TyInteger.DEFAULT_KIND
                    && actualTy is TyInteger
                    && actualTy.kind == TyInteger.DEFAULT_KIND
                ) {
                    val pathExpr = tailExprForType as? MvPathExpr
                    if (pathExpr != null) {
                        val binding = resolvePathElement(pathExpr, expectedTy) as? MvPatBinding
                        if (binding != null) {
                            refineIntegerBinding(binding, expectedTy, pathExpr.ancestorOrSelf())
                            actualTy = expectedTy.mslScopeRefined(msl)
                        }
                    }
                }



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

                        if (!coerce(tailExprForType, actualTy, expectedTy)) {


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
                    ctx.writeExprTy(tailExprForType, actualTy)
                    return TyUnit
                }


                ctx.writeExprTy(tailExprForType, actualTy)
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
                    if (ctx.isTypeInferred(expr)) {
                        val ty = ctx.getExprType(expr)
                        if (ty is TyNever) {
                            return true
                        }
                    } else {
                        // If not inferred yet, infer it now.
                        val ty = expr.inferType()
                        if (ty is TyNever) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun resolveTypeVarsIfPossible(ty: Ty): Ty = ctx.resolveTypeVarsIfPossible(ty)

    private fun processStatement(stmt: MvStmt) {
        when (stmt) {
            is MvLetStmt -> {
                val explicitTy = stmt.type?.loweredType(msl)
                val expr = stmt.initializer?.expr
                val pat = stmt.pat
                val inferredTy =
                    if (expr != null) {
                        val inferredTy = expr.inferType(Expectation.maybeHasType(explicitTy))
                        val coercedTy = if (explicitTy != null && coerce(expr, inferredTy, explicitTy)) {
                            explicitTy
                        } else {
                            inferredTy
                        }
                        coercedTy
                    } else {
                        pat?.anonymousTyVar() ?: TyUnknown
                    }
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
                val expr = stmt.expr
                if (expr is MvAbortExpr) {
                    // Ensure MvAbortExpr is typed as TyNever.
                    val ty = expr.inferType()
                    ctx.writeExprTy(expr, ty)
                } else {

                    val ty = expr.inferType()
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
                is MvMacroCallExpr,
                is MvRefExpr -> this.ctx.writeExprExpectedTy(expr, it)
            }
        }

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
                // Return TyNever directly to avoid later mslScopeRefined handling.
                return ty
            }
            is MvPathExpr -> inferPathExprTy(expr, expected)
            is MvBorrowExpr -> inferBorrowExprTy(expr, expected)
            is MvCallExpr -> inferCallExprTy(expr, expected)
            is MvAssertMacroExpr -> inferMacroCallExprTy(expr)
            is MvMacroCallExpr -> inferMacroCallExprTy(expr, expected, receiverTy = null)
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
            return exprTy
        }
        val refinedExprTy = exprTy.mslScopeRefined(msl)
        ctx.writeExprTy(expr, refinedExprTy)
        val retrievedTy = ctx.getExprType(expr)

        ctx.exprTypes.forEach {
        }
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


        val expectedType = expected.onlyHasTy(ctx)
        val item = resolvePathElement(pathExpr, expectedType) ?: run {
            val name = pathExpr.path.referenceName
            if (name != null) {
                val schema = pathExpr.containingModule?.schemaList?.firstOrNull { it.name == name }
                if (schema != null) {
                    ctx.writeExprTy(pathExpr, TyUnit)
                    return TyUnit
                }
            }
            return TyUnknown
        }


        val ty = when (item) {
            is MvPatBinding -> {
                val bindingTy = ctx.getBindingType(item)

                val isReturnExpr = pathExpr.parent is MvReturnExpr
                val isTailExpr = pathExpr.ancestorOrSelf<MvCodeBlock>()?.returningExpr == pathExpr
                if (!msl
                    && (isReturnExpr || isTailExpr)
                    && bindingTy is TyInteger
                    && bindingTy.kind == TyInteger.DEFAULT_KIND
                    && expectedType is TyInteger
                    && expectedType.kind != TyInteger.DEFAULT_KIND
                ) {
                    refineIntegerBinding(item, expectedType, pathExpr.ancestorOrSelf())
                    expectedType.mslScopeRefined(msl)
                } else if (msl && (bindingTy is TyInteger || bindingTy is TyInfer.IntVar)) {
                    TyNum
                } else {
                    bindingTy.mslScopeRefined(msl)
                }
            }
            is MvConst -> item.type?.loweredType(msl) ?: TyUnknown
            is MvGlobalVariableStmt -> item.type?.loweredType(true) ?: TyUnknown
            is MvNamedFieldDecl -> item.type?.loweredType(msl) ?: TyUnknown
            is MvStruct -> {
                if (project.moveLanguageFeatures.indexExpr && pathExpr.parent is MvIndexExpr) {
                    TyLowering.lowerPath(pathExpr.path, item, ctx.msl)
                } else {
                    TyUnit
                }
            }
            is MvSchema -> TyUnit
            is MvFunctionLike -> TyUnit
            is MvEnumVariant -> item.enumItem.declaredType(ctx.msl)
            is MvModule -> TyUnknown
            else -> debugErrorOrFallback(
                "Referenced item ${item.elementType} " +
                        "of ref expr `${pathExpr.text}` at ${pathExpr.location} cannot be inferred into type",
                TyUnknown
            )
        }
        ctx.writeExprTy(pathExpr, ty)
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
        val directResolved = resolvedItems.singleOrNull { it.isVisible }
            ?.element
            ?.let { resolveAliases(it) }
        if (directResolved != null) return directResolved

        if (pathElement is MvPathExpr && path.path == null) {
            val referenceName = path.referenceName ?: return null
            val resolutionCtx = ResolutionContext(path, isCompletion = false)
            val fallbackKind = PathKind.UnqualifiedPath(ITEM_NAMESPACES)
            val fallbackItems =
                collectResolveVariantsAsScopeEntries(referenceName) {
                    processPathResolveVariantsWithExpectedType(
                        resolutionCtx,
                        fallbackKind,
                        expectedType,
                        it
                    )
                }.map { ResolvedItem.from(it, path) }
            if (fallbackItems.isNotEmpty()) {
                ctx.writePath(path, fallbackItems)
                return fallbackItems.singleOrNull { it.isVisible }
                    ?.element
                    ?.let { resolveAliases(it) }
            }
        }
        return null
    }

    private fun refineIntegerBinding(
        binding: MvPatBinding,
        expectedType: TyInteger,
        ownerBlock: MvCodeBlock?
    ) {
        if (!isImplicitDefaultIntegerBinding(binding)) return
        ctx.writePatTy(binding, expectedType)
        if (ownerBlock == null) return
        ownerBlock.descendantsOfType<MvPathExpr>().forEach { expr ->
            val resolved = resolvePathElement(expr, expectedType)
            if (resolved == binding) {
                ctx.writeExprTy(expr, expectedType.mslScopeRefined(msl))
            }
        }
    }

    private fun inferAssignmentExprTy(assignExpr: MvAssignmentExpr): Ty {
        val lhsTy = assignExpr.expr.inferType()
        val rhsExpr = assignExpr.initializer.expr
        if (rhsExpr != null) {
            val rhsTy = if (rhsExpr is MvIfExpr) {
                rhsExpr.inferType(expected = Expectation.maybeHasType(lhsTy))
            } else {
                val expectInteger =
                    lhsTy is TyInfer.TyVar &&
                        rhsExpr is MvLitExpr &&
                        isUnsuffixedIntegerLiteral(rhsExpr)
                val rhsExpected =
                    if (expectInteger) Expectation.maybeHasType(TyInfer.IntVar()) else Expectation.NoExpectation
                rhsExpr.inferType(expected = rhsExpected)
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
                    val itemTy = ctx.instantiateMethodOrPath<TyFunction>(path, namedItem)
                        ?.first
                        ?: namedItem.declaredType(msl)
                    itemTy
                }
                is MvPatBinding -> {
                    ctx.getBindingType(namedItem) as? TyLambda
                        ?: TyFunction.unknownTyFunction(callExpr.project, callExpr.valueArguments.size)
                }
                else -> TyFunction.unknownTyFunction(callExpr.project, callExpr.valueArguments.size)
            }
        val funcTy = ctx.resolveTypeVarsIfPossible(baseTy) as TyCallable
        val paramTypes = funcTy.paramTypes

        val expectedInputTys = if (expected.onlyHasTy(ctx) is TyUnit && callExpr.parent is MvExprStmt) {
            emptyList()
        } else {
            expectedInputsForExpectedOutput(expected, funcTy.retType, paramTypes)
        }


        val isAbortCall = callExpr.path.text == "abort"
        val hasExplicitTypeParameters = if (isAbortCall) {
            false
        } else {
            callExpr.path.typeArguments.isNotEmpty()
        }

        val argExprs = collectCallArgumentExprs(callExpr).toMutableList()
        val leadingEmptyArgs = countLeadingEmptyArgs(callExpr.text)
        if (leadingEmptyArgs > 0 && argExprs.size < paramTypes.size) {
            val toInsert = minOf(leadingEmptyArgs, paramTypes.size - argExprs.size)
            repeat(toInsert) { argExprs.add(0, null) }
        }
        inferArgumentTypes(
            paramTypes,
            expectedInputTys,
            argExprs.map { InferArg.ArgExpr(it) },
            hasExplicitTypeParameters)

        writeCallableType(callExpr, funcTy, method = false)

        if (leadingEmptyArgs > 0
            && callExpr.argumentExprs.isNotEmpty()
            && paramTypes.size > callExpr.argumentExprs.size
        ) {
            val actualArgs = callExpr.argumentExprs.filterNotNull()
            actualArgs.forEachIndexed { index, expr ->
                val paramIndex = index + leadingEmptyArgs
                val expectedTy = paramTypes.getOrNull(paramIndex) ?: return@forEachIndexed
                val argTy = if (ctx.isTypeInferred(expr)) ctx.getExprType(expr) else expr.inferType(expected = Expectation.maybeHasType(expectedTy))
                if (!ctx.tryCoerce(argTy, expectedTy).isOk) {
                    ctx.reportTypeError(TypeError.TypeMismatch(expr, expectedTy, argTy))
                }
            }
        }

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
        if (methodCall.excl != null) {
            val macroFunction = resolveMacroMethodCall(methodCall, receiverTy)
            if (macroFunction != null) {
                ctx.resolvedMethodCalls[methodCall] = macroFunction
                return inferMethodCallFromFunction(receiverTy, methodCall, expected, macroFunction)
            }
        }

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

        val methodArgs = collectCallArgumentExprs(methodCall).toMutableList()
        val leadingEmptyMethodArgs = countLeadingEmptyArgs(methodCall.text)
        if (leadingEmptyMethodArgs > 0 && methodArgs.size < methodTy.paramTypes.size) {
            val toInsert = minOf(leadingEmptyMethodArgs, methodTy.paramTypes.size - methodArgs.size)
            repeat(toInsert) { methodArgs.add(0, null) }
        }
        inferArgumentTypes(
            methodTy.paramTypes,
            expectedInputTys,
            listOf(InferArg.SelfType(receiverTy)) + methodArgs.map { InferArg.ArgExpr(it) }
        )

        writeCallableType(methodCall, methodTy, method = true)

        return methodTy.retType
    }

    private fun resolveMacroMethodCall(methodCall: MvMethodCall, receiverTy: Ty): MvFunction? {
        val resolutionCtx = ResolutionContext(methodCall, isCompletion = false)
        val resolvedMethods =
            collectMethodOrPathResolveVariants(methodCall, resolutionCtx) {
                processMethodResolveVariants(methodCall, receiverTy, msl, it)
            }
        return resolvedMethods.asSequence()
            .filter { it.isVisible }
            .mapNotNull { it.element as? MvFunction }
            .firstOrNull { it.isMacro }
    }

    private fun inferMethodCallFromFunction(
        receiverTy: Ty,
        methodCall: MvMethodCall,
        expected: Expectation,
        function: MvFunction
    ): Ty {
        val baseTy =
            ctx.instantiateMethodOrPath<TyFunction>(methodCall, function)
                ?.first
                ?: TyFunction.unknownTyFunction(methodCall.project, 1 + methodCall.valueArguments.size)
        val methodTy = ctx.resolveTypeVarsIfPossible(baseTy) as TyCallable

        val expectedInputTys =
            expectedInputsForExpectedOutput(expected, methodTy.retType, methodTy.paramTypes)

        val methodArgs = collectCallArgumentExprs(methodCall).toMutableList()
        val leadingEmptyMethodArgs = countLeadingEmptyArgs(methodCall.text)
        if (leadingEmptyMethodArgs > 0 && methodArgs.size < methodTy.paramTypes.size) {
            val toInsert = minOf(leadingEmptyMethodArgs, methodTy.paramTypes.size - methodArgs.size)
            repeat(toInsert) { methodArgs.add(0, null) }
        }
        inferArgumentTypes(
            methodTy.paramTypes,
            expectedInputTys,
            listOf(InferArg.SelfType(receiverTy)) + methodArgs.map { InferArg.ArgExpr(it) }
        )

        writeCallableType(methodCall, methodTy, method = true)

        return methodTy.retType
    }

    sealed class InferArg {
        data class SelfType(val selfTy: Ty): InferArg()
        data class ArgExpr(val expr: MvExpr?): InferArg()
    }

    private fun collectCallArgumentExprs(callable: MvCallable): List<MvExpr?> {
        val argList = callable.valueArgumentList ?: return callable.argumentExprs
        val segments = splitTopLevelArgs(argList.text)
        if (segments.none { it.isBlank() }) {
            return callable.argumentExprs
        }

        val exprs = callable.argumentExprs.filterNotNull().iterator()
        val result = mutableListOf<MvExpr?>()
        for (segment in segments) {
            if (segment.isBlank()) {
                result.add(null)
            } else {
                result.add(if (exprs.hasNext()) exprs.next() else null)
            }
        }
        return result
    }

    private fun countLeadingEmptyArgs(text: String): Int {
        val openIndex = text.indexOf('(')
        if (openIndex == -1) return 0
        val closeIndex = text.indexOf(')', openIndex + 1).let { if (it == -1) text.length else it }
        var i = openIndex + 1
        var count = 0
        while (i < closeIndex) {
            while (i < closeIndex && text[i].isWhitespace()) {
                i++
            }
            if (i < closeIndex && text[i] == ',') {
                count++
                i++
                continue
            }
            break
        }
        return count
    }

    private fun splitTopLevelArgs(text: String): List<String> {
        val openIndex = text.indexOf('(')
        if (openIndex == -1) return emptyList()
        val closeIndex = text.lastIndexOf(')')
        val endIndex = if (closeIndex == -1 || closeIndex < openIndex) text.length else closeIndex
        val inner = text.substring(openIndex + 1, endIndex)
        val segments = mutableListOf<String>()
        val buf = StringBuilder()
        var paren = 0
        var bracket = 0
        var brace = 0
        var inString = false
        var escape = false
        for (ch in inner) {
            if (inString) {
                buf.append(ch)
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '\"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '\"' -> {
                    inString = true
                    buf.append(ch)
                }
                '(' -> {
                    paren++
                    buf.append(ch)
                }
                ')' -> {
                    if (paren > 0) paren--
                    buf.append(ch)
                }
                '[' -> {
                    bracket++
                    buf.append(ch)
                }
                ']' -> {
                    if (bracket > 0) bracket--
                    buf.append(ch)
                }
                '{' -> {
                    brace++
                    buf.append(ch)
                }
                '}' -> {
                    if (brace > 0) brace--
                    buf.append(ch)
                }
                ',' -> {
                    if (paren == 0 && bracket == 0 && brace == 0) {
                        segments.add(buf.toString())
                        buf.setLength(0)
                    } else {
                        buf.append(ch)
                    }
                }
                else -> buf.append(ch)
            }
        }
        segments.add(buf.toString())
        if (inner.trimEnd().endsWith(",") && segments.lastOrNull()?.isBlank() == true) {
            segments.removeAt(segments.lastIndex)
        }
        return segments
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
                    val isTypeParameter = formalInputTy is TyTypeParameter || formalInputTy is TyInfer.TyVar || hasExplicitTypeParameters
                    if (argExprTy is TyInteger && expectedTy is TyInteger) {
                    }


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

    fun inferMacroCallExprTy(macroExpr: MvMacroCallExpr): Ty =
        inferMacroCallExprTy(macroExpr, Expectation.NoExpectation, receiverTy = null)

    private fun inferMacroCallExprTy(
        macroExpr: MvMacroCallExpr,
        expected: Expectation,
        receiverTy: Ty?
    ): Ty {
        val resolvedMacro = resolveMacroFunction(macroExpr)
        if (resolvedMacro != null) {
            return inferMacroCallFromFunction(macroExpr, resolvedMacro, expected, receiverTy)
        }
        macroSemanticService.inferReturnType(macroExpr, this)?.let { return it }
        return inferBuiltinMacroCallExprTy(macroExpr)
    }

    private fun resolveMacroFunction(macroExpr: MvMacroCallExpr): MvFunction? {
        val referenceName = macroExpr.path.referenceName ?: return null
        val resolutionCtx = ResolutionContext(macroExpr, isCompletion = false)
        val entries = collectResolveVariantsAsScopeEntries(referenceName) {
            processNestedScopesUpwards(macroExpr, FUNCTIONS, resolutionCtx, it)
        }
        val path = macroExpr.path
        val resolvedItems = entries.map { ResolvedItem.from(it, path) }
        ctx.writePath(path, resolvedItems)
        return entries.asSequence()
            .filter { it.isVisibleFrom(path) }
            .mapNotNull { resolveAliases(it.element) as? MvFunction }
            .firstOrNull { it.isMacro }
    }

    private fun inferMacroCallFromFunction(
        macroExpr: MvMacroCallExpr,
        function: MvFunction,
        expected: Expectation,
        receiverTy: Ty?
    ): Ty {
        val path = macroExpr.path
        val baseTy =
            ctx.instantiateMethodOrPath<TyFunction>(path, function)
                ?.first
                ?: function.declaredType(msl)
        val funcTy = ctx.resolveTypeVarsIfPossible(baseTy) as TyCallable
        val paramTypes = funcTy.paramTypes

        val expectedInputTys =
            if (expected.onlyHasTy(ctx) is TyUnit && macroExpr.parent is MvExprStmt) {
                emptyList()
            } else {
                expectedInputsForExpectedOutput(expected, funcTy.retType, paramTypes)
            }

        val hasExplicitTypeParameters = path.typeArguments.isNotEmpty()

        val argExprs = collectCallArgumentExprs(macroExpr).toMutableList()
        val leadingEmptyArgs = countLeadingEmptyArgs(macroExpr.text)
        if (leadingEmptyArgs > 0 && argExprs.size < paramTypes.size) {
            val toInsert = minOf(leadingEmptyArgs, paramTypes.size - argExprs.size)
            repeat(toInsert) { argExprs.add(0, null) }
        }

        val inferArgs =
            if (receiverTy != null) {
                listOf(InferArg.SelfType(receiverTy)) + argExprs.map { InferArg.ArgExpr(it) }
            } else {
                argExprs.map { InferArg.ArgExpr(it) }
            }
        inferArgumentTypes(
            paramTypes,
            expectedInputTys,
            inferArgs,
            hasExplicitTypeParameters
        )

        writeCallableType(macroExpr, funcTy, method = receiverTy != null)

        if (receiverTy == null
            && leadingEmptyArgs > 0
            && macroExpr.argumentExprs.isNotEmpty()
            && paramTypes.size > macroExpr.argumentExprs.size
        ) {
            val actualArgs = macroExpr.argumentExprs.filterNotNull()
            actualArgs.forEachIndexed { index, expr ->
                val paramIndex = index + leadingEmptyArgs
                val expectedTy = paramTypes.getOrNull(paramIndex) ?: return@forEachIndexed
                val argTy = if (ctx.isTypeInferred(expr)) {
                    ctx.getExprType(expr)
                } else {
                    expr.inferType(expected = Expectation.maybeHasType(expectedTy))
                }
                if (!ctx.tryCoerce(argTy, expectedTy).isOk) {
                    ctx.reportTypeError(TypeError.TypeMismatch(expr, expectedTy, argTy))
                }
            }
        }

        return funcTy.retType
    }

    private fun inferBuiltinMacroCallExprTy(macroExpr: MvMacroCallExpr): Ty {
        val pathText = macroExpr.path.text.removeSuffix("!")
        if (pathText == "vector" && macroExpr.vectorLitItems != null) {
            // vector! already has a dedicated handling method inferVectorLitExpr
            return TyUnknown
        }
        return TyUnknown
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

        val tyVar = TyInfer.TyVar()
        val explicitTy = litExpr.typeArgument?.type?.loweredType(msl)
        if (explicitTy != null) {
            ctx.combineTypes(tyVar, explicitTy)
        }

        val exprs = litExpr.vectorLitItems.exprList
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
        if (resolvedTyVar is TyInfer.TyVar && !ctx.msl && exprs.isNotEmpty()) {
            ctx.combineTypes(tyVar, TyInteger.default())
        }

        val vectorTy = ctx.resolveTypeVarsIfPossible(TyVector(tyVar))
        return vectorTy
    }

    private fun inferIndexExprTy(indexExpr: MvIndexExpr): Ty {
        val receiverTy = indexExpr.receiverExpr.inferType()
        val argTy = indexExpr.argExpr.inferType()

        // compiler v2 only in non-msl
        if (!ctx.msl && !project.moveLanguageFeatures.indexExpr) {
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
                        val expected = if (ctx.msl) TyNum else TyInfer.IntVar()
                        ctx.reportTypeError(TypeError.TypeMismatch(indexExpr.argExpr, expected, argTy))
                        TyUnknown
                    }
                }
            }
            receiverTy is TyAdt -> {
                if (isResourceIndexExpr(indexExpr, receiverTy)) {
                    indexExpr.argExpr.inferTypeCoercableTo(TyAddress)
                    return receiverTy
                }
                val syntaxFunction = resolveIndexSyntaxFunction(indexExpr, receiverTy)
                if (syntaxFunction == null) {
                    ctx.reportTypeError(TypeError.IndexingIsNotAllowed(indexExpr.receiverExpr, receiverTy))
                    return TyUnknown
                }

                val funcTy =
                    syntaxFunction
                        .declaredType(msl)
                        .substitute(syntaxFunction.tyInfers) as TyFunction
                val expectedInputTys =
                    expectedInputsForExpectedOutput(Expectation.NoExpectation, funcTy.retType, funcTy.paramTypes)

                inferArgumentTypes(
                    funcTy.paramTypes,
                    expectedInputTys,
                    listOf(
                        InferArg.SelfType(receiverTy),
                        InferArg.ArgExpr(indexExpr.argExpr)
                    )
                )

                val resolvedRetTy = ctx.resolveTypeVarsIfPossible(funcTy.retType)
                return when (resolvedRetTy) {
                    is TyReference -> resolvedRetTy.innerTy()
                    else -> resolvedRetTy
                }
            }
            else -> {
                ctx.reportTypeError(TypeError.IndexingIsNotAllowed(indexExpr.receiverExpr, receiverTy))
                TyUnknown
            }
        }
    }

    private fun isResourceIndexExpr(indexExpr: MvIndexExpr, receiverTy: TyAdt): Boolean {
        val receiverPath = indexExpr.receiverExpr as? MvPathExpr ?: return false
        val resolved = resolvePathElement(receiverPath, receiverTy) ?: return false
        val structItem = resolved as? MvStruct ?: return false
        return structItem.hasKey
    }

    private fun resolveIndexSyntaxFunction(
        indexExpr: MvIndexExpr,
        receiverTy: Ty
    ): MvFunction? {
        val moveProject = indexExpr.moveProject ?: return null
        val itemModule = receiverTy.itemModule(moveProject) ?: return null
        val candidates = itemModule.syntaxIndexFunctions()
        return candidates.firstOrNull { function ->
            // #[syntax(index)] should not depend on receiver-style function feature gate.
            val selfParam = function.parameters.firstOrNull()?.takeIf { it.name == "self" }
                ?: return@firstOrNull false
            val selfTy = selfParam.type?.loweredType(msl) ?: return@firstOrNull false
            val selfTyWithTyVars =
                selfTy.deepFoldTyTypeParameterWith { tp -> TyInfer.TyVar(tp) }
            TyReference.isCompatibleWithAutoborrow(receiverTy, selfTyWithTyVars, msl)
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
        val leftExpr = rangeExpr.exprList.firstOrNull()
        val rightExpr = rangeExpr.exprList.drop(1).firstOrNull()
        if (!ctx.msl && leftExpr is MvLitExpr && rightExpr is MvLitExpr) {
            val leftLiteral = leftExpr.integerLiteral ?: leftExpr.hexIntegerLiteral
            val rightLiteral = rightExpr.integerLiteral ?: rightExpr.hexIntegerLiteral
            if (leftLiteral != null && rightLiteral != null) {
                val leftHasSuffix = leftExpr.text.contains("u") || leftExpr.text.contains("i")
                val rightHasSuffix = rightExpr.text.contains("u") || rightExpr.text.contains("i")
                if (!leftHasSuffix && !rightHasSuffix) {
                    val intVar = TyInfer.IntVar()
                    leftExpr.inferType(expected = intVar)
                    rightExpr.inferType(expected = intVar)
                    return TyRange(intVar)
                }
            }
        }

        val leftTy = leftExpr?.inferType() ?: TyUnknown
        rightExpr?.inferType(expected = leftTy)
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

            if (!typeErrorEncountered) {
                // Integer inference: prefer the concrete kind over implicit default kind.
                if (leftTy is TyInteger && rightTy is TyInteger) {
                    val leftImplicitDefault = isImplicitDefaultIntegerExpr(leftExpr, leftTy)
                    val rightImplicitDefault = isImplicitDefaultIntegerExpr(rightExpr, rightTy)
                    val rightIsExplicitIntegerLiteral =
                        rightExpr is MvLitExpr && !isUnsuffixedIntegerLiteral(rightExpr)
                    val leftIsExplicitIntegerLiteral =
                        leftExpr is MvLitExpr && !isUnsuffixedIntegerLiteral(leftExpr)
                    // If left is implicit default and right is concrete, choose right.
                    if (leftImplicitDefault && !rightImplicitDefault
                        && (leftExpr is MvLitExpr || rightExpr !is MvLitExpr || rightIsExplicitIntegerLiteral)
                    ) {
                        tryRefineIntegerExpr(leftExpr, rightTy)
                        coerce(leftExpr, leftTy, expected = rightTy)
                        return rightTy
                    }
                    // If right is implicit default and left is concrete, choose left.
                    if (rightImplicitDefault && !leftImplicitDefault
                        && (rightExpr is MvLitExpr || leftExpr !is MvLitExpr || leftIsExplicitIntegerLiteral)
                    ) {
                        tryRefineIntegerExpr(rightExpr, leftTy)
                        coerce(rightExpr, rightTy, expected = leftTy)
                        return leftTy
                    }
                }
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
                    ctx.combineTypes(leftTy, rightTy)
                    return TyBool
                }
            }


            if (ctx.msl) {
                if (leftTy is TyNum || rightTy is TyNum || leftTy is TyInteger || rightTy is TyInteger) {
                    return TyBool
                }
            }

            if (leftTy is TyInteger && rightTy is TyInteger) {
                val leftImplicitDefault = isImplicitDefaultIntegerExpr(leftExpr, leftTy)
                val rightImplicitDefault = isImplicitDefaultIntegerExpr(rightExpr, rightTy)
                when {
                    leftImplicitDefault && !rightImplicitDefault && rightExpr !is MvLitExpr -> {
                        tryRefineIntegerExpr(leftExpr, rightTy)
                    }
                    rightImplicitDefault && !leftImplicitDefault && leftExpr !is MvLitExpr -> {
                        tryRefineIntegerExpr(rightExpr, leftTy)
                    }
                    !leftImplicitDefault && !rightImplicitDefault && leftTy.kind != rightTy.kind -> {
                        ctx.reportTypeError(
                            TypeError.IncompatibleArgumentsToBinaryExpr(binaryExpr, leftTy, rightTy, op)
                        )
                    }
                }
            } else if (ctx.combineTypes(leftTy, rightTy).isErr) {
                ctx.reportTypeError(
                    TypeError.IncompatibleArgumentsToBinaryExpr(binaryExpr, leftTy, rightTy, op)
                )
            }
        }
        return TyBool
    }

    private fun tryRefineIntegerExpr(expr: MvExpr, expectedTy: TyInteger) {
        when (expr) {
            is MvPathExpr -> {
                val binding = resolvePathElement(expr, expectedTy) as? MvPatBinding ?: return
                val bindingTy = ctx.getBindingType(binding)
                if (bindingTy is TyInteger
                    && bindingTy.kind == TyInteger.DEFAULT_KIND
                    && isImplicitDefaultIntegerBinding(binding)
                ) {
                    if (bindingReferencedInSpecBlock(binding)) return
                    refineIntegerBinding(binding, expectedTy, expr.ancestorOrSelf())
                }
            }
            is MvIndexExpr -> {
                val receiver = expr.receiverExpr as? MvPathExpr ?: return
                val binding = resolvePathElement(receiver, expectedTy) as? MvPatBinding ?: return
                val bindingTy = ctx.getBindingType(binding)
                val vectorTy = bindingTy as? TyVector ?: return
                val itemTy = vectorTy.item as? TyInteger ?: return
                if (itemTy.kind == TyInteger.DEFAULT_KIND && isImplicitDefaultVectorBinding(binding)) {
                    val refined = TyVector(expectedTy)
                    ctx.writePatTy(binding, refined)
                    ctx.writeExprTy(expr, expectedTy.mslScopeRefined(msl))
                    val ownerBlock = receiver.ancestorOrSelf<MvCodeBlock>()
                    if (ownerBlock != null) {
                        ownerBlock.descendantsOfType<MvPathExpr>().forEach { pathExpr ->
                            val resolved = resolvePathElement(pathExpr, expectedTy)
                            if (resolved == binding) {
                                ctx.writeExprTy(pathExpr, refined.mslScopeRefined(msl))
                            }
                        }
                    }
                }
            }
            is MvLitExpr -> {
                if (isUnsuffixedIntegerLiteral(expr)) {
                    ctx.writeExprTy(expr, expectedTy.mslScopeRefined(msl))
                }
            }
        }
    }

    private fun isImplicitDefaultIntegerExpr(expr: MvExpr, ty: TyInteger): Boolean {
        if (ty.kind != TyInteger.DEFAULT_KIND) return false
        return when (expr) {
            is MvLitExpr -> isUnsuffixedIntegerLiteral(expr)
            is MvPathExpr -> {
                val binding = resolvePathElement(expr, ty) as? MvPatBinding ?: return false
                isImplicitDefaultIntegerBinding(binding)
            }
            is MvIndexExpr -> {
                val receiver = expr.receiverExpr as? MvPathExpr ?: return false
                val binding = resolvePathElement(receiver, ty) as? MvPatBinding ?: return false
                isImplicitDefaultVectorBinding(binding)
            }
            else -> false
        }
    }

    private fun isImplicitDefaultIntegerBinding(binding: MvPatBinding): Boolean {
        val owner = binding.owner
        return when (owner) {
            is MvLetStmt -> {
                if (owner.type != null) return false
                val initExpr = owner.initializer?.expr
                initExpr is MvLitExpr && isUnsuffixedIntegerLiteral(initExpr)
            }
            is MvFunctionParameter -> owner.type == null
            is MvSchemaFieldStmt -> owner.type == null
            else -> false
        }
    }

    private fun bindingReferencedInSpecBlock(binding: MvPatBinding): Boolean {
        val bindingName = binding.name ?: return false
        val codeBlock = binding.ancestorOrSelf<MvCodeBlock>() ?: return false
        val specBindingNames = specBindingNamesCache.getOrPut(codeBlock) {
            val names = mutableSetOf<String>()
            codeBlock.descendantsOfType<MvSpecCodeBlock>().forEach { specBlock ->
                specBlock.descendantsOfType<MvPathExpr>().forEach { pathExpr ->
                    pathExpr.path.referenceName?.let { names.add(it) }
                }
            }
            names
        }
        return bindingName in specBindingNames
    }

    private fun isImplicitDefaultVectorBinding(binding: MvPatBinding): Boolean {
        val owner = binding.owner
        return when (owner) {
            is MvLetStmt -> {
                if (owner.type != null) return false
                val initExpr = owner.initializer?.expr as? MvVectorLitExpr ?: return false
                vectorLiteralHasOnlyUnsuffixedIntegers(initExpr)
            }
            is MvFunctionParameter -> owner.type == null
            is MvSchemaFieldStmt -> owner.type == null
            else -> false
        }
    }

    private fun vectorLiteralHasOnlyUnsuffixedIntegers(litExpr: MvVectorLitExpr): Boolean {
        val exprs = litExpr.vectorLitItems.exprList
        if (exprs.isEmpty()) return true
        return exprs.all { it is MvLitExpr && isUnsuffixedIntegerLiteral(it) }
    }

    private fun isUnsuffixedIntegerLiteral(litExpr: MvLitExpr): Boolean {
        val literal = litExpr.integerLiteral ?: litExpr.hexIntegerLiteral ?: return false
        return TyInteger.fromSuffixedLiteral(literal) == null
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
        if (ctx.msl && (litExpr.integerLiteral != null || litExpr.hexIntegerLiteral != null)) {
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
            val literal = litExpr.integerLiteral ?: litExpr.hexIntegerLiteral
            val isUnsuffixedInteger =
                !ctx.msl && literal != null && TyInteger.fromSuffixedLiteral(literal) == null
            if (expectedTy is TyInfer.IntVar && isUnsuffixedInteger) {
                return expectedTy
            }
            val inferredForCoercion =
                if (isUnsuffixedInteger && expectedTy is TyInteger) expectedTy else litTy
            coerce(litExpr, inferredForCoercion, expectedTy)
            if (isUnsuffixedInteger && expectedTy is TyInteger) {
                return if (expectedTy != TyInteger.default()) expectedTy else TyInteger.default()
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
        val elseBlock = ifExpr.elseBlock ?: run {
            ctx.writeExprTy(ifExpr, TyUnit)
            return TyUnit
        }
        val actualElseTy =
            elseBlock.codeBlock?.inferBlockType(expected, coerce = true)
                ?: elseBlock.inlineBlock?.expr?.inferTypeCoercableTo(expected)



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
        if (actualElseTy != null && expectedTy == null) {
            val elseExpr = elseBlock.inlineBlock?.expr ?: elseBlock.codeBlock?.expr
            if (elseExpr != null) {
                if (expectedElseTy is TyReference && actualElseTy is TyReference) {
                    coerce(elseExpr, actualElseTy.referenced, expectedElseTy.referenced)
                } else {
                    if (actualIfTy != null && !ctx.tryCoerce(actualElseTy, actualIfTy).isOk) {
                        ctx.reportTypeError(TypeError.TypeMismatch(elseExpr, actualIfTy, actualElseTy))
                    } else if (!ctx.tryCoerce(actualElseTy, expectedElseTy).isOk) {
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


        val finalResultTy =
            if (isInsideExprStmt && !isInsideAssignment && resultTy is TyUnknown) {
                TyUnit
            } else {
                resultTy
            }
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
            } else {
                val bindingPat = iterCondition.patBinding
                if (bindingPat != null) {
                    this.ctx.writePatTy(bindingPat, TyUnknown)
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
                if (!ctx.isTypeInferred(loopLike)) {
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
                if (!ctx.isTypeInferred(loopLike)) {
                    ctx.writeExprTy(loopLike, TyNever)
                }
            }
            is MvLoopExpr -> {
                // Type the loop expression itself.
                if (!ctx.isTypeInferred(loopLike)) {
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
