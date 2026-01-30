package org.sui.lang.core.psi.ext

import com.intellij.psi.util.descendantsOfType
import org.sui.lang.core.psi.MvMatchPat
import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.MvPat
import org.sui.lang.core.psi.MvPathExpr

val MvMatchPat.pat: MvPat? get() = childOfType<MvPat>()

val MvMatchPat.pathExpr: MvPathExpr? get() = childOfType<MvPathExpr>()

val MvMatchPat.bindings: List<MvNamedElement>
    get() {
        val result = mutableListOf<MvNamedElement>()
        pat?.let { result.addAll(it.bindings) }
        // TODO: pathExpr may also have bindings, need to check if PathExpr has any named elements
        return result
    }
