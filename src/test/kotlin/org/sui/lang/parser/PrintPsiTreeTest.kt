
package org.sui.lang.parser

import com.intellij.psi.util.PsiTreeUtil
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvExpr
import org.sui.utils.tests.MvProjectTestBase

class PrintPsiTreeTest : MvProjectTestBase() {
    fun `test print function body psi tree`() {
        val code = """
            module 0x1::M {
                fun call(): u8 {
                    let a = 1;
                    a;
                }
                fun main() {
                    let b = call();
                    b;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(MoveFileType, code)
        val callFunction = PsiTreeUtil.findChildrenOfType(psiFile, MvFunction::class.java)
            .first { it.identifier?.text == "call" }

        println("Call function: ${callFunction.text}")
        println("Function body PSI tree:")
        callFunction.codeBlock?.let { printPsiTree(it, 0) }


        println("\nAll MvExpr elements in file:")
        val allExprs = PsiTreeUtil.findChildrenOfType(psiFile, MvExpr::class.java)
        allExprs.forEach { expr ->
            println("Type: ${expr.javaClass.simpleName}, Text: '${expr.text}'")
        }
    }

    private fun printPsiTree(element: MvCodeBlock, indent: Int) {
        val indentStr = "  ".repeat(indent)
        println("${indentStr}Type: ${element.javaClass.simpleName}")
        println("${indentStr}Text: ${element.text}")
        println("${indentStr}Children:")
        for (child in element.children) {
            printPsiTree(child, indent + 1)
        }
    }

    private fun printPsiTree(element: com.intellij.psi.PsiElement, indent: Int) {
        val indentStr = "  ".repeat(indent)
        println("${indentStr}Type: ${element.javaClass.simpleName}")
        println("${indentStr}Text: '${element.text}'")
        println("${indentStr}Children:")
        for (child in element.children) {
            printPsiTree(child, indent + 1)
        }
    }
}
