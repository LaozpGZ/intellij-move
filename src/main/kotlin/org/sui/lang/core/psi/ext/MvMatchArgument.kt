package org.sui.lang.core.psi.ext

import org.sui.lang.core.psi.MvMatchArgument
import org.sui.lang.core.psi.MvExpr

val MvMatchArgument.expr: MvExpr?
    get() = this.getExpr()
