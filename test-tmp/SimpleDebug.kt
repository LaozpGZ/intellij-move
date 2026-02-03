import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvFile

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

                    println("=== PSI Tree ===")
                    printPsiTree(psiFile, 0)
                }
            } else {
                println("Test file not found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun printPsiTree(element: com.intellij.psi.PsiElement, level: Int) {
    val indent = "  ".repeat(level)
    println("${indent}${element.text} [${element.javaClass.simpleName}]")
    for (child in element.children) {
        printPsiTree(child, level + 1)
    }
}
