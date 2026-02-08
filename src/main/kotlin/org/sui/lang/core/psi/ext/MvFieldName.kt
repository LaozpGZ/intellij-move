package org.sui.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.sui.lang.MvElementTypes
import org.sui.lang.core.psi.MvElement
import org.sui.lang.core.resolve.ref.MvMandatoryReferenceElement

private val STRUCT_FIELD_NAME_TOKENS: TokenSet = TokenSet.create(
    MvElementTypes.IDENTIFIER,
    MvElementTypes.INTEGER_LITERAL,
)

fun MvElement.findStructFieldNameElement(): PsiElement? =
    this.node.findChildByType(STRUCT_FIELD_NAME_TOKENS)?.psi

fun MvMandatoryReferenceElement.fieldDeclName(): String? =
    (this as? MvElement)?.findStructFieldNameElement()?.text

