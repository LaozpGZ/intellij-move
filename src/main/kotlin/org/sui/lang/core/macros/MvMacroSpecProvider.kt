package org.sui.lang.core.macros

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension hook for supplying additional macro specs from external plugins.
 */
interface MvMacroSpecProvider {
    fun macroSpecs(): Collection<MacroSpec>

    companion object {
        val EP_NAME: ExtensionPointName<MvMacroSpecProvider> =
            ExtensionPointName.create("org.sui.lang.macroSpecProvider")
    }
}
