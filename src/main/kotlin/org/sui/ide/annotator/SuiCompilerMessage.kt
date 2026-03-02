package org.sui.ide.annotator

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.TestOnly

data class SuiCompilerMessage(
    val message: String,
    val severityLevel: String,
    val spans: List<SuiCompilerSpan>
) {
    val mainSpan: SuiCompilerSpan?
        get() {
            val validSpan = spans.filter { it.isValid() }.firstOrNull { it.isPrimary } ?: return null
            return validSpan
        }

    fun toTestString(): String {
        return "$severityLevel: '$message' at ${mainSpan?.toTestString()}"
    }

    companion object {
        @TestOnly
        fun forTest(
            message: String,
            severityLevel: String,
            filename: String,
            location: String,
        ): SuiCompilerMessage {
            val match =
                Regex("""\[\((?<lineStart>\d+), (?<columnStart>\d+)\), \((?<lineEnd>\d+), (?<columnEnd>\d+)\)]""")
                    .find(location) ?: error("invalid string")
            val (lineStart, colStart, lineEnd, colEnd) = match.destructured
            val span = SuiCompilerSpan(
                filename,
                lineStart.toInt(),
                lineEnd.toInt(),
                colStart.toInt(),
                colEnd.toInt(),
                true,
                null
            )
            return SuiCompilerMessage(message, severityLevel, listOf(span))
        }
    }
}

data class SuiCompilerSpan(
    val filename: String,
    val lineStart: Int,
    val lineEnd: Int,
    val columnStart: Int,
    val columnEnd: Int,
    val isPrimary: Boolean,
    val label: String?,
) {
    fun toTextRange(document: Document): TextRange? {
        val startOffset = toOffset(document, lineStart, columnStart)
        val endOffset = toOffset(document, lineEnd, columnEnd)
        return if (startOffset != null && endOffset != null && startOffset < endOffset) {
            TextRange(startOffset, endOffset)
        } else {
            null
        }
    }

    fun isValid(): Boolean =
        lineEnd > lineStart || (lineEnd == lineStart && columnEnd >= columnStart)

    fun toTestString(): String {
        return "$filename:[($lineStart, $columnStart), ($lineEnd, $columnEnd)]"
    }

    companion object {
        @Suppress("NAME_SHADOWING")
        fun toOffset(document: Document, line: Int, column: Int): Int? {
            val line = line - 1
            val column = column - 1
            if (line < 0 || line >= document.lineCount) return null
            return (document.getLineStartOffset(line) + column)
                .takeIf { it <= document.textLength }
        }
    }
}
