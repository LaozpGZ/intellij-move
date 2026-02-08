package org.sui.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import org.sui.ide.MoveIcons
import org.sui.lang.core.psi.MvTupleFieldDecl
import org.sui.lang.core.psi.MvTupleFields
import org.sui.lang.core.psi.impl.MvNamedElementImpl
import javax.swing.Icon

val MvTupleFieldDecl.tupleFieldsBlock: MvTupleFields?
    get() = parent as? MvTupleFields

val MvTupleFieldDecl.fieldOwner: MvFieldsOwner?
    get() = tupleFieldsBlock?.parent as? MvFieldsOwner

val MvTupleFieldDecl.index: Int
    get() = tupleFieldsBlock?.tupleFieldDeclList?.indexOf(this) ?: -1

abstract class MvTupleFieldDeclMixin(node: ASTNode) : MvNamedElementImpl(node), MvTupleFieldDecl {

    override fun getName(): String = index.toString()

    override fun getIcon(flags: Int): Icon = MoveIcons.STRUCT_FIELD

    override fun getPresentation(): ItemPresentation {
        val index = this.index
        val fieldType = this.type.text
        return PresentationData(
            "$index: $fieldType",
            this.locationString(true),
            MoveIcons.STRUCT_FIELD,
            null
        )
    }
}

