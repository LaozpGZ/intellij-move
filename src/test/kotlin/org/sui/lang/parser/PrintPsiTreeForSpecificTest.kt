
package org.sui.lang.parser

import com.intellij.psi.util.PsiTreeUtil
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.MvExprStmt
import org.sui.lang.core.psi.MvSpecExprStmt
import org.sui.utils.tests.MvProjectTestBase

class PrintPsiTreeForSpecificTest : MvProjectTestBase() {
    fun `test print specific test case psi tree`() {
        val code = """
            module 0x1::mod {
                fun call() {
                    let a = 1;
                    spec {
                        a = a + 1;
                        a;
                    };
                    a;
                  //^ integer
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
        allExprs.forEachIndexed { index, expr ->
            println("Index $index: Type: ${expr.javaClass.simpleName}, Text: '${expr.text}'")
        }


        println("\nAll MvExprStmt elements in file:")
        val allExprStmts = PsiTreeUtil.findChildrenOfType(psiFile, MvExprStmt::class.java)
        allExprStmts.forEachIndexed { index, stmt ->
            println("Index $index: Type: ${stmt.javaClass.simpleName}, Text: '${stmt.text}'")
            stmt.expr?.let { expr ->
                println("  Expr Type: ${expr.javaClass.simpleName}, Text: '${expr.text}'")
            }
        }


        println("\nAll MvSpecExprStmt elements in file:")
        val allSpecExprStmts = PsiTreeUtil.findChildrenOfType(psiFile, MvSpecExprStmt::class.java)
        allSpecExprStmts.forEachIndexed { index, stmt ->
            println("Index $index: Type: ${stmt.javaClass.simpleName}, Text: '${stmt.text}'")

            val children = stmt.children
            for (child in children) {
                if (child is MvExpr) {
                    println("  Expr Type: ${child.javaClass.simpleName}, Text: '${child.text}'")
                }
            }
        }


        println("\nFunction body children details:")
        callFunction.codeBlock?.let { codeBlock ->
            codeBlock.children.forEachIndexed { index, child ->
                println("Child $index: Type: ${child.javaClass.simpleName}, Text: '${child.text}'")

                child.children.forEachIndexed { subIndex, subChild ->
                    println("  SubChild $subIndex: Type: ${subChild.javaClass.simpleName}, Text: '${subChild.text}'")
                }
            }
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
