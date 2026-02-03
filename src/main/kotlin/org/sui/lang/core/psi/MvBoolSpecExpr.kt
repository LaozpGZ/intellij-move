package org.sui.lang.core.psi

interface MvBoolSpecExpr : MvExpr {
    // Keep this interface empty to avoid JVM signature clashes with generated getExpr().
    // Related inference logic lives in TypeInferenceWalker.kt.
}
