package org.sui.lang.types

import com.intellij.psi.util.descendantsOfType
import org.sui.ide.presentation.text
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.types.infer.inference
import org.sui.utils.tests.MvTestBase
import org.sui.utils.tests.InlineFile
import java.io.File

class PrintBreakExprTest : MvTestBase() {
    fun `test break expr type`() {
        val code = """
            module 0x1::m {
                fun main() {
                    while (true) {
                        break
                    }
                }
            }
        """.trimIndent()

        InlineFile(myFixture, code, "test_break.move")
        val file = myFixture.file

        val outputFile = File("/Users/gz/Desktop/test_output.txt")
        outputFile.writeText("File PSI tree:\n")

        printPsiTree(file, 0, outputFile)

        // Check if all expressions are typified
        val notTypifiedExprs = file.descendantsOfType<MvExpr>().toList()
            .filter { expr ->
                expr.inference(false)?.hasExprType(expr) == false
            }
        if (notTypifiedExprs.isNotEmpty()) {
            outputFile.appendText("\nSome expressions are not typified:\n")
            notTypifiedExprs.forEach { expr ->
                outputFile.appendText("\t${expr.text} (${expr.javaClass.simpleName})\n")
            }
        } else {
            outputFile.appendText("\nAll expressions are typified.\n")
        }

        // Print type information for each expression
        val allExprs = file.descendantsOfType<MvExpr>().toList()
        outputFile.appendText("\nExpression types:\n")
        allExprs.forEach { expr ->
            val type = expr.inference(false)?.getExprType(expr)?.text(true)
            outputFile.appendText("\t${expr.text} (${expr.javaClass.simpleName}): ${type}\n")
        }

        println("Test output written to: ${outputFile.absolutePath}")
    }

    private fun printPsiTree(element: Any?, depth: Int, outputFile: File) {
        if (element == null) return

        val indent = "  ".repeat(depth)
        if (element is com.intellij.psi.PsiElement) {
            outputFile.appendText("${indent}${element.javaClass.simpleName} \"${element.text.trim()}\"\n")
            for (child in element.children) {
                printPsiTree(child, depth + 1, outputFile)
            }
        }
    }
}
