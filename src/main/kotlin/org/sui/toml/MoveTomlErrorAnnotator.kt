package org.sui.toml

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.util.text.findTextRange
import org.sui.cli.MoveEdition
import org.sui.ide.annotator.MvAnnotatorBase
import org.sui.openapiext.stringValue
import org.toml.lang.psi.TomlTable

class MoveTomlErrorAnnotator : MvAnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        if (file.name != "Move.toml") {
            return
        }
        if (element !is TomlTable) return

        val tableKey = element.header.key ?: return
        when {
            tableKey.textMatches("addresses") -> annotateAddresses(element, holder)
            tableKey.textMatches("package") -> annotatePackageEdition(element, holder)
        }
    }

    companion object {
        private val DIEM_ADDRESS_REGEX = Regex("[0-9a-fA-F]{1,64}")
    }

    private fun annotateAddresses(element: TomlTable, holder: AnnotationHolder) {
        for (tomlKeyValue in element.entries) {
            val tomlValue = tomlKeyValue.value ?: continue
            val rawStringValue = tomlValue.stringValue() ?: continue
            if (rawStringValue == "_") continue
            val tomlString = rawStringValue.removePrefix("0x")
            val stringRange =
                tomlValue.text.findTextRange(tomlString)?.shiftRight(tomlValue.textOffset)
                    ?: tomlValue.textRange
            if (tomlString.length > 64) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Invalid address: no more than 64 symbols allowed"
                )
                    .range(stringRange)
                    .create()
                return
            }
            if (DIEM_ADDRESS_REGEX.matchEntire(tomlString) == null) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Invalid address: only hex symbols are allowed"
                )
                    .range(stringRange)
                    .create()
                return
            }
        }
    }

    private fun annotatePackageEdition(element: TomlTable, holder: AnnotationHolder) {
        val editionEntry = element.entries.firstOrNull { it.key.textMatches("edition") } ?: return
        val tomlValue = editionEntry.value ?: return
        val rawStringValue = tomlValue.stringValue()
        if (rawStringValue == null) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Invalid edition: expected a string literal"
            )
                .range(tomlValue.textRange)
                .create()
            return
        }
        if (MoveEdition.fromToml(rawStringValue) != null) return
        val expected = MoveEdition.supportedEditionValues.joinToString(", ")
        val stringRange =
            tomlValue.text.findTextRange(rawStringValue)?.shiftRight(tomlValue.textOffset)
                ?: tomlValue.textRange
        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "Invalid edition: expected one of $expected"
        )
            .range(stringRange)
            .create()
    }
}
