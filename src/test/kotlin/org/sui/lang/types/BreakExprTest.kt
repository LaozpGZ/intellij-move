package org.sui.lang.types

import org.sui.utils.tests.types.TypificationTestCase

class BreakExprTest : TypificationTestCase() {
    fun `test break expr psi type`() {
        testExpr("""
            module 0x1::m {
                fun main() {
                    while (true) {
                        break
                        //^ <never>
                    }
                }
            }
        """)
    }

    fun `test break value expr psi type`() {
        testExpr("""
            module 0x1::m {
                fun main() {
                    while (true) {
                        break 1u8
                        //^ <never>
                    }
                }
            }
        """)
    }
}
