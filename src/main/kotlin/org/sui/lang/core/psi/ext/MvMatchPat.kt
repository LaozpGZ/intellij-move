package org.sui.lang.core.psi.ext

import com.intellij.psi.util.descendantsOfType
import org.sui.lang.core.psi.MvMatchPat
import org.sui.lang.core.psi.MvNamedElement
import org.sui.lang.core.psi.MvPat
import org.sui.lang.core.psi.MvPathPat

val MvMatchPat.pat: MvPat? get() = childOfType<MvPat>()

val MvMatchPat.pathPat: MvPathPat? get() = childOfType<MvPathPat>()

val MvMatchPat.bindings: List<MvNamedElement>
    get() {
        val result = mutableListOf<MvNamedElement>()
        pat?.let { result.addAll(it.bindings) }
        // TODO: pathPat may also have bindings, need to check if PathPat has any named elements
        return result
    }
