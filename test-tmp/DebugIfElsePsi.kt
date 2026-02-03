import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvElseBlock
import org.sui.lang.core.psi.MvFile
import org.sui.lang.core.psi.MvFunction

fun main() {
    ApplicationManager.getApplication().invokeAndWait {
        try {
            val project: Project? = null

            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file:///Users/gz/Documents/GitHub/intellij-move/test-tmp/debug-if-else.move")
            if (virtualFile != null) {
                println("Found test file: ${virtualFile.name}")
                println("File type: ${virtualFile.fileType}")

                if (virtualFile.fileType is MoveFileType) {
                    val psiFile = PsiManager.getInstance(project!!).findFile(virtualFile) as MvFile
                    println("Parsed as Move file: ${psiFile.name}")

                    val functions = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, MvFunction::class.java)
                    println("Found ${functions.size} functions:")
                    functions.forEachIndexed { index, function ->
                        println("  Function $index: ${function.name}")

                        val codeBlocks = PsiTreeUtil.getChildrenOfTypeAsList(function.codeBlock, MvCodeBlock::class.java)
                        println("  Found ${codeBlocks.size} code blocks:")
                        codeBlocks.forEachIndexed { blockIndex, block ->
                            println("    Code block $blockIndex: ${block.text}")

                            val exprStmts = block.stmtList
                            println("    Statements: $exprStmts")
                            exprStmts.forEachIndexed { stmtIndex, stmt ->
                                println("    Statement $stmtIndex: ${stmt.text} (${stmt.javaClass.simpleName})")
                                println("    Children: ${stmt.children.map { it.text to it.javaClass.simpleName }}")


                                if (stmt.text.contains("if")) {
                                    println("    Found if statement: ${stmt.text}")

                                    val ifExpr = PsiTreeUtil.findChildOfType(stmt, org.sui.lang.core.psi.MvIfExpr::class.java)
                                    if (ifExpr != null) {
                                        println("      If expr: ${ifExpr.text}")
                                        println("      If expr class: ${ifExpr.javaClass.simpleName}")

                                        val ifCodeBlock = ifExpr.codeBlock
                                        if (ifCodeBlock != null) {
                                            println("      If code block: ${ifCodeBlock.text}")
                                            println("      If returning expr: ${ifCodeBlock.returningExpr?.text}")
                                            println("      If returning expr class: ${ifCodeBlock.returningExpr?.javaClass?.simpleName}")
                                        } else {
                                            println("      No if code block")
                                        }

                                        val inlineBlock = ifExpr.inlineBlock
                                        if (inlineBlock != null) {
                                            println("      Inline block: ${inlineBlock.text}")
                                            println("      Inline block expr: ${inlineBlock.expr?.text}")
                                        } else {
                                            println("      No inline block")
                                        }

                                        println("      If tail expr: ${ifExpr.tailExpr?.text}")

                                        val elseBlock = ifExpr.elseBlock
                                        if (elseBlock != null) {
                                            println("      Else block: ${elseBlock.text}")
                                            println("      Else block class: ${elseBlock.javaClass.simpleName}")

                                            val elseCodeBlock = elseBlock.codeBlock
                                            if (elseCodeBlock != null) {
                                                println("        Else code block: ${elseCodeBlock.text}")
                                                println("        Else returning expr: ${elseCodeBlock.returningExpr?.text}")
                                            } else {
                                                println("        No else code block")
                                            }

                                            val elseInlineBlock = elseBlock.inlineBlock
                                            if (elseInlineBlock != null) {
                                                println("        Else inline block: ${elseInlineBlock.text}")
                                                println("        Else inline block expr: ${elseInlineBlock.expr?.text}")
                                            } else {
                                                println("        No else inline block")
                                            }

                                            println("        Else tail expr: ${elseBlock.tailExpr?.text}")
                                        } else {
                                            println("      No else block")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                println("Test file not found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
