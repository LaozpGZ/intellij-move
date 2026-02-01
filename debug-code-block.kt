import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.descendantsOfType
import org.sui.lang.MoveLanguage
import org.sui.lang.MoveFileType
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvExprStmt
import org.sui.lang.core.psi.MvItemSpecBlockExpr
import java.io.File

fun main() {
    val code = """
        module 0x1::main {
            fun foo() {
                let res = 1;
                let i = 0;
                while ({
                    spec {
                        invariant res == 10;
                        invariant 0 <= i && i <= 5;
                    };
                    i < 5
                }) {
                    res = res * 10;
                    i = i + 1;
                }
            }
        }
    """.trimIndent()

    // Create a simple test environment
    try {
        // We'll use reflection to create a simple parser
        // Note: This is a simplified version, actual IntelliJ platform testing requires a more complex environment
        println("=== Parsing Move Code ===")
        println(code)
        println()

        // Try to parse code directly (this may not work, but we can try)
        try {
            val factory = PsiFileFactory.getInstance(null)
            val file = factory.createFileFromText("debug.move", MoveFileType, code)

            println("=== Parsing Successful ===")
            println("File type: ${file.javaClass.name}")
            println()

            // Find all code blocks
            val codeBlocks = file.descendantsOfType<MvCodeBlock>().toList()
            println("Found ${codeBlocks.size} code blocks")
            println()

            codeBlocks.forEachIndexed { index, block ->
                println("=== Code Block $index ===")

                // Print all statements
                block.stmtList.forEachIndexed { stmtIndex, stmt ->
                    println("  Statement $stmtIndex: ${stmt.javaClass.simpleName}")
                    if (stmt is MvExprStmt) {
                        println("    Expression: ${stmt.expr.text}")
                        println("    Expression type: ${stmt.expr.javaClass.simpleName}")

                        // Check if it's a spec block
                        if (stmt.text.contains("spec")) {
                            println("    This is a spec block")
                        }
                    }
                    println()
                }

                // Print tail expression information
                val tailExpr = block.expr
                println("  Tail expression: ${tailExpr?.text}")
                if (tailExpr != null) {
                    println("  Tail expression type: ${tailExpr.javaClass.simpleName}")
                }
                println()
            }

        } catch (e: Exception) {
            println("Failed to parse code: ${e.message}")
            e.printStackTrace()
        }

    } catch (e: Exception) {
        println("Initialization failed: ${e.message}")
        e.printStackTrace()
    }
}

