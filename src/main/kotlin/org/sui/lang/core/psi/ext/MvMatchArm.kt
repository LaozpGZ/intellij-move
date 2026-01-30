package org.sui.lang.core.psi.ext

import org.sui.lang.core.psi.MvMatchArm
import org.sui.lang.core.psi.MvMatchArmGuard
import org.sui.lang.core.psi.MvMatchBody
import org.sui.lang.core.psi.MvMatchExpr
import org.sui.lang.core.psi.MvExpr

val MvMatchArm.matchBody: MvMatchBody get() = this.parent as MvMatchBody
val MvMatchArm.matchExpr: MvExpr get() = this.matchBody.parent as MvMatchExpr
val MvMatchArm.matchArmGuard: MvMatchArmGuard? get() = this.getMatchArmGuard()
val MvMatchArmGuard.expr: MvExpr get() = this.getExpr()
val MvMatchArm.expr: MvExpr? get() = this.getExpr()
