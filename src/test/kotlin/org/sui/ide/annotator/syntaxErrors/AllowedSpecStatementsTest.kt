package org.sui.ide.annotator.syntaxErrors

import org.sui.ide.annotator.MvSyntaxErrorAnnotator
import org.sui.utils.tests.annotation.AnnotatorTestCase

class AllowedSpecStatementsTest : AnnotatorTestCase(MvSyntaxErrorAnnotator::class) {
    fun `test assert assume allowed in inline spec blocks`() = checkWarnings(
        """
        module 0x1::m {
            fun main() {
                spec {
                    assert 1 == 1;
                    assume 1 == 1;
                }
            }
        }
    """
    )

    fun `test assert assume allowed in item spec blocks`() = checkWarnings(
        """
        module 0x1::m {
            fun main() {}
            spec main {
                assert 1 == 1;
                assume 1 == 1;
            }
        }
    """
    )

    fun `test update allowed in inline and item specs`() = checkWarnings(
        """
        module 0x1::m {
            struct S has key { v: u64 }
            fun main(addr: address) {
                spec {
                    update global<S>(addr).v = 1;
                }
            }
            spec main {
                update global<S>(addr).v = 2;
            }
        }
    """
    )
}
