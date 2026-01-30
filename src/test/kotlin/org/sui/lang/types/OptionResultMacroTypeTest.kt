package org.sui.lang.types

import org.junit.Test
import org.sui.utils.tests.types.TypificationTestCase

class OptionResultMacroTypeTest : TypificationTestCase() {

    @Test
    fun `test option macro type inference`() = testExpr(
        """
        module 0x1::test {
            use std::option;

            fun main() {
                let o = option!(1);
                o;
              //^ <unknown>
            }
        }
        """
    )

    @Test
    fun `test result macro type inference`() = testExpr(
        """
        module 0x1::test {
            use std::result;

            fun main() {
                let r = result!(1, 2);
                r;
              //^ <unknown>
            }
        }
        """
    )
}
