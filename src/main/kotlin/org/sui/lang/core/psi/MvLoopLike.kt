package org.sui.lang.core.psi

interface MvLoopLike : MvElement {
    val codeBlock: MvCodeBlock?
    val inlineBlock: MvInlineBlock?
    val labelDecl: MvLabelDecl?
}
