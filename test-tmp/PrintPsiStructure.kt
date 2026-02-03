import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvCallExpr
import org.sui.lang.core.psi.MvExprStmt
import org.sui.lang.core.psi.MvFile
import org.sui.lang.core.psi.MvFunction

fun main() {

    ApplicationManager.getApplication().invokeAndWait {
        try {

            val project: Project? = null


            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file:///Users/gz/Documents/GitHub/intellij-move/test-tmp/test.move")
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


                        val exprStmts = PsiTreeUtil.getChildrenOfTypeAsList(function.bodyBlock?.codeBlock, MvExprStmt::class.java)
                        println("  Found ${exprStmts.size} expression statements:")
                        exprStmts.forEachIndexed { stmtIndex, stmt ->
                            println("    Expression statement $stmtIndex: ${stmt.text}")


                            val callExpr = PsiTreeUtil.getChildOfType(stmt, MvCallExpr::class.java)
                            if (callExpr != null) {
                                println("    Found function call: ${callExpr.text}")


                                var current: Any? = callExpr
                                var level = 0
                                while (current != null) {
                                    println("    Level $level: ${current.javaClass.simpleName}")
                                    current = if (current is com.intellij.psi.PsiElement) {
                                        current.parent
                                    } else {
                                        null
                                    }
                                    level++
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