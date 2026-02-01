import com.intellij.psi.util.descendantsOfType
import org.sui.lang.core.psi.MvCodeBlock
import org.sui.lang.core.psi.MvExpr
import org.sui.lang.core.psi.MvExprStmt
import org.sui.lang.core.psi.MvItemSpecBlockExpr
import org.sui.utils.tests.InlineFile
import org.sui.utils.tests.MvTestBase

class DebugBlockExprTest : MvTestBase() {

    fun `test block with spec and condition`() {
        val code = """
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
        """.trimIndent()

        InlineFile(myFixture, code, "debug.move")
        val file = myFixture.file

        val codeBlocks = file.descendantsOfType<MvCodeBlock>().toList()
        println("Found ${codeBlocks.size} code blocks")

        codeBlocks.forEachIndexed { index, block ->
            println("\n=== Code block $index ===")

            // Check statement types
            block.stmtList.forEachIndexed { stmtIndex, stmt ->
                println("  Statement $stmtIndex: ${stmt::class.simpleName}")

                // If it's an ExprStmt, print the expression type
                if (stmt is MvExprStmt) {
                    println("    Expr: ${stmt.expr.text}")
                    println("    Expr type: ${stmt.expr::class.simpleName}")
                }

                // Check if it contains a spec block
                if (stmt.text.contains("spec")) {
                    println("    Contains spec block")
                }
            }

            // Check expr property
            val expr = block.expr
            println("  Tail expr: ${expr?.text}")
            expr?.let { println("  Tail expr type: ${it::class.simpleName}") }
        }
    }
}
