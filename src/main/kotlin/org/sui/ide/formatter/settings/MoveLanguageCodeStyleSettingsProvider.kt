package org.sui.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.sui.lang.MoveLanguage

class MvLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = MoveLanguage

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
        MoveCodeStyleSettings(settings)

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings
    ): CodeStyleConfigurable {
        return object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                MoveCodeStyleMainPanel(currentSettings, settings)
        }
    }

    override fun getConfigurableDisplayName() = "Sui Move Language"

    override fun getCodeSample(settingsType: SettingsType): String =
        when (settingsType) {
            SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE
//            SettingsType.SPACING_SETTINGS -> SPACING_SAMPLE
//            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> WRAPPING_AND_BRACES_SAMPLE
//            SettingsType.BLANK_LINES_SETTINGS -> BLANK_LINES_SAMPLE
            else -> ""
        }

    override fun getIndentOptionsEditor(): IndentOptionsEditor = MoveIndentOptionsEditor()

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions
    ) {
        commonSettings.RIGHT_MARGIN = 120
        commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true

        // Make default behavior consistent with rustfmt
        commonSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
        commonSettings.LINE_COMMENT_ADD_SPACE = true
        commonSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false

        // Match rustfmt defaults: continuation indent follows regular indent.
        indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.INDENT_SIZE
    }
}

private class MoveIndentOptionsEditor : SmartIndentOptionsEditor() {
    override fun isModified(
        settings: CodeStyleSettings,
        options: CommonCodeStyleSettings.IndentOptions
    ): Boolean {
        return super.isModified(settings, options.normalizedForMove())
    }

    override fun reset(
        settings: CodeStyleSettings,
        options: CommonCodeStyleSettings.IndentOptions
    ) {
        super.reset(settings, options.normalizedForMove())
    }

    override fun apply(
        settings: CodeStyleSettings,
        options: CommonCodeStyleSettings.IndentOptions
    ) {
        super.apply(settings, options)
        options.CONTINUATION_INDENT_SIZE = options.INDENT_SIZE
    }
}

private fun CommonCodeStyleSettings.IndentOptions.normalizedForMove(): CommonCodeStyleSettings.IndentOptions {
    val copy = clone() as CommonCodeStyleSettings.IndentOptions
    copy.CONTINUATION_INDENT_SIZE = copy.INDENT_SIZE
    return copy
}

private fun sample(@org.intellij.lang.annotations.Language("Sui Move") code: String) = code.trim()

private val INDENT_SAMPLE = sample(
    """
module std::main {
    struct Vector {
        x: u64,
        y: u64,
        z: u64
    }

    fun add(this: &Vector, other: &Vector): Vector {
        Vector {
            x: this.x + other.x,
            y: this.y + other.y,
            z: this.z + other.z,
        }
    }
}    
"""
)
