
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.sui.lang.MoveLanguage
import org.sui.lang.core.psi.MvAbortExpr
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvFile
import org.sui.lang.core.psi.ext.descendantsOfType

fun main() {
    // Create a simple Move code string containing an abort expression
    val moveCode = """
        module 0x1::m {
            fun main() {
                abort 1
            }
        }
    """.trimIndent()

    // Create a temporary project and file
    val project = ProjectManager.getInstance().openProjects.firstOrNull()
        ?: throw RuntimeException("No open project")

    // Create PsiFile
    val psiFileFactory = PsiFileFactory.getInstance(project)
    val psiFile = psiFileFactory.createFileFromText("debug.move", MoveLanguage, moveCode) as MvFile

    // Find all expressions
    val allExprs = psiFile.descendantsOfType<MvExpr>()
    println("Total number of expressions: ${allExprs.size}")

    allExprs.forEachIndexed { index, expr ->
        println("Expression $index: ${expr.text}")
        println("  Type: ${expr.javaClass.simpleName}")
        println("  Is MvAbortExpr: ${expr is MvAbortExpr}")

        if (expr is MvAbortExpr) {
            println("  Sub-expression: ${expr.expr?.text}")
        }
    }
}
