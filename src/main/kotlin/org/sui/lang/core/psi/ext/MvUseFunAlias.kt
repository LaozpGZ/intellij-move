package org.sui.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.sui.lang.core.psi.MvUseFunAlias
import org.sui.lang.core.psi.impl.MvNameIdentifierOwnerImpl

abstract class MvUseFunAliasMixin(node: ASTNode) : MvNameIdentifierOwnerImpl(node),
                                                   MvUseFunAlias
