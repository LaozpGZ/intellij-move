package org.sui.lang.core.psi

interface MvBoolSpecExpr : MvExpr {
    // 为了避免与生成的 getExpr() 方法发生 JVM 签名冲突，我们移除了该属性
    // 相关逻辑已在 TypeInferenceWalker.kt 中实现
}
