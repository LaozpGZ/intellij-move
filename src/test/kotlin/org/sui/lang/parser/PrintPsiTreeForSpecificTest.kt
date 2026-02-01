
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

        // 查找所有 MvExpr 元素
        println("\nAll MvExpr elements in file:")
        val allExprs = PsiTreeUtil.findChildrenOfType(psiFile, MvExpr::class.java)
        allExprs.forEachIndexed { index, expr ->
            println("Index $index: Type: ${expr.javaClass.simpleName}, Text: '${expr.text}'")
        }

        // 查找所有 MvExprStmt 元素
        println("\nAll MvExprStmt elements in file:")
        val allExprStmts = PsiTreeUtil.findChildrenOfType(psiFile, MvExprStmt::class.java)
        allExprStmts.forEachIndexed { index, stmt ->
            println("Index $index: Type: ${stmt.javaClass.simpleName}, Text: '${stmt.text}'")
            stmt.expr?.let { expr ->
                println("  Expr Type: ${expr.javaClass.simpleName}, Text: '${expr.text}'")
            }
        }

        // 查找所有 SpecExprStmt 元素
        println("\nAll MvSpecExprStmt elements in file:")
        val allSpecExprStmts = PsiTreeUtil.findChildrenOfType(psiFile, MvSpecExprStmt::class.java)
        allSpecExprStmts.forEachIndexed { index, stmt ->
            println("Index $index: Type: ${stmt.javaClass.simpleName}, Text: '${stmt.text}'")
            // 尝试获取 SpecExprStmt 内部的表达式
            val children = stmt.children
            for (child in children) {
                if (child is MvExpr) {
                    println("  Expr Type: ${child.javaClass.simpleName}, Text: '${child.text}'")
                }
            }
        }

        // 打印函数体中每个子元素的详细信息
        println("\nFunction body children details:")
        callFunction.codeBlock?.let { codeBlock ->
            codeBlock.children.forEachIndexed { index, child ->
                println("Child $index: Type: ${child.javaClass.simpleName}, Text: '${child.text}'")
                // 打印每个子元素的子元素信息
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
