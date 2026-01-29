package org.sui.ide.refactoring.optimizeImports

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.sui.lang.core.psi.MvModule
import org.sui.lang.core.psi.ext.firstItem
import org.sui.lang.core.psi.ext.descendantsOfType

class MergeImportsTest : OptimizeImportsTestBase() {

    fun `test self import unchanged if no items`() {
        val before = """
module 0x1::Coin { public fun call() {} }
module 0x1::Main {
    use 0x1::Coin::Self;
    fun main() {
        Coin::call();
    }
}
    """.trimIndent()

        val expected = """
module 0x1::Coin { public fun call() {} }
module 0x1::Main {
    use 0x1::Coin::Self;

    fun main() {
        Coin::call();
    }
}
    """.trimIndent()

        // Test directly without using checkEditorAction to capture output
        InlineFile(before)
        println("=== Before optimization ===")
        println(myFixture.file.text)

        // Print module structure information
        val modules = myFixture.file.descendantsOfType<MvModule>()
        modules.forEachIndexed { index, module ->
            println("\nModule $index (${module.name}):")
            println("  First child: ${module.firstChild.text}")
            println("  First item: ${module.firstItem?.text}")
            if (module.firstItem != null) {
                println("  First item prev sibling: ${module.firstItem!!.prevSibling?.text}")
                println("  First item prev sibling type: ${module.firstItem!!.prevSibling?.javaClass}")
            }
            println("  Children: ${module.children.map { "${it.text} (${it.javaClass.simpleName})" }}")
        }

        myFixture.performEditorAction("OptimizeImports")

        println("\n=== After optimization ===")
        println(myFixture.file.text)

        // Print module structure information
        val modulesAfter = myFixture.file.descendantsOfType<MvModule>()
        modulesAfter.forEachIndexed { index, module ->
            println("\nModule $index (${module.name}):")
            println("  First child: ${module.firstChild.text}")
            println("  First item: ${module.firstItem?.text}")
            if (module.firstItem != null) {
                println("  First item prev sibling: ${module.firstItem!!.prevSibling?.text}")
                println("  First item prev sibling type: ${module.firstItem!!.prevSibling?.javaClass}")
            }
            println("  Children: ${module.children.map { "${it.text} (${it.javaClass.simpleName})" }}")
        }

        // Compare actual result with expected result
        val actualText = myFixture.file.text
        if (actualText != expected) {
            println("\n=== Differences found ===")
            val actualLines = actualText.lines()
            val expectedLines = expected.lines()
            val maxLines = maxOf(actualLines.size, expectedLines.size)

            for (i in 0 until maxLines) {
                val actualLine = actualLines.getOrElse(i) { ">> END OF ACTUAL <<" }
                val expectedLine = expectedLines.getOrElse(i) { ">> END OF EXPECTED <<" }

                if (actualLine != expectedLine) {
                    println("\nLine ${i + 1}:")
                    println("  Actual:   '${actualLine}'")
                    println("  Expected: '${expectedLine}'")
                }
            }
        } else {
            println("\n=== No differences found ===")
        }

        myFixture.checkResult(expected)
    }
}

//    fun `test merge item into existing group`() = doTest(
//        """
//module 0x1::M1 { struct S1 {} struct S2 {} struct S3 {} }
//module 0x1::Main {
//    use 0x1::M1::S1;
//    use 0x1::M1::{S2, S3};
//
//    fun call(s1: S1, s2: S2, s3: S3) {}
//}
//    """, """
//module 0x1::M1 { struct S1 {} struct S2 {} struct S3 {} }
//module 0x1::Main {
//    use 0x1::M1::{S1, S2, S3};
//
//    fun call(s1: S1, s2: S2, s3: S3) {}
//}
//    """
//    )
//
//    fun `test simple module import merges with item group`() = doTest(
//        """
//    module 0x1::Coin {
//        struct Coin {}
//        public fun get_coin(): Coin {}
//    }
//    module 0x1::Main {
//        use 0x1::Coin;
//        use 0x1::Coin::Coin;
//
//        fun call(): Coin {
//            Coin::get_coin()
//        }
//    }
//    """, """
//    module 0x1::Coin {
//        struct Coin {}
//        public fun get_coin(): Coin {}
//    }
//    module 0x1::Main {
//        use 0x1::Coin::{Self, Coin};
//
//        fun call(): Coin {
//            Coin::get_coin()
//        }
//    }
//    """
//    )
//
//    fun `test test_only has its own separate group`() = doTest("""
//    module 0x1::Coin {
//        struct Coin {}
//        struct Coin2 {}
//        public fun get_coin(): Coin {}
//        #[test_only]
//        public fun get_coin_2(): Coin {}
//    }
//    module 0x1::Main {
//        use 0x1::Coin;
//        use 0x1::Coin::Coin;
//        #[test_only]
//        use 0x1::Coin::Coin2;
//        #[test_only]
//        use 0x1::Coin::get_coin_2;
//
//        fun call(): Coin {
//            Coin::get_coin()
//        }
//
//        #[test]
//        fun test(c: Coin2) {
//            get_coin_2();
//        }
//    }
//    """, """
//    module 0x1::Coin {
//        struct Coin {}
//        struct Coin2 {}
//        public fun get_coin(): Coin {}
//        #[test_only]
//        public fun get_coin_2(): Coin {}
//    }
//    module 0x1::Main {
//        use 0x1::Coin::{Self, Coin};
//
//        #[test_only]
//        use 0x1::Coin::{Coin2, get_coin_2};
//
//        fun call(): Coin {
//            Coin::get_coin()
//        }
//
//        #[test]
//        fun test(c: Coin2) {
//            get_coin_2();
//        }
//    }
//    """)

//    fun `test merge items into group`() = doTest("""
//module 0x1::M1 { struct S1 {} struct S2 {} }
//module 0x1::Main {
//    use 0x1::M1::S1;
//    use 0x1::M1::S2;
//
//    fun call(s1: S1, s2: S2) {}
//}
//    """, """
//module 0x1::M1 { struct S1 {} struct S2 {} }
//module 0x1::Main {
//    use 0x1::M1::{S1, S2};
//
//    fun call(s1: S1, s2: S2) {}
//}
//    """)
