package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.ide.MoveIcons
import org.sui.lang.core.psi.MvModule
import org.sui.lang.core.psi.MvTypeAlias
import org.sui.lang.core.psi.impl.MvNameIdentifierOwnerImpl
import org.sui.lang.core.types.ItemQualName
import org.sui.lang.core.types.infer.loweredType
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.core.types.ty.TyUnknown
import javax.swing.Icon

val MvTypeAlias.module: MvModule? get() = this.parent as? MvModule

abstract class MvTypeAliasMixin(node: ASTNode): MvNameIdentifierOwnerImpl(node),
                                                MvTypeAlias {

    override val qualName: ItemQualName?
        get() {
            val itemName = this.name ?: return null
            val moduleFQName = this.module?.qualName ?: return null
            return ItemQualName(this, moduleFQName.address, moduleFQName.itemName, itemName)
        }

    override fun declaredType(msl: Boolean): Ty {
        val rhsType = this.type ?: return TyUnknown
        return rhsType.loweredType(msl)
    }

    override fun getIcon(flags: Int): Icon = MoveIcons.ENUM
}
