package org.sui.lang.types

import org.junit.Test
import org.sui.ide.inspections.fixes.CompilerV2Feat.MACRO_FUNCTIONS
import org.sui.ide.inspections.fixes.CompilerV2Feat.RECEIVER_STYLE_FUNCTIONS
import org.sui.utils.tests.CompilerV2Features
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
              //^ std::option::Option<u64>
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
              //^ std::result::Result<u64, u64>
            }
        }
        """
    )

    @Test
    fun `test bcs macro type inference`() = testExpr(
        """
        module 0x1::test {
            use std::bcs;

            fun main() {
                let bytes = bcs!(1);
                bytes;
              //^ vector<u8>
            }
        }
        """
    )

    @Test
    @CompilerV2Features(RECEIVER_STYLE_FUNCTIONS, MACRO_FUNCTIONS)
    fun `test method macro call type inference`() = testExpr(
        """
        module 0x1::test {
            struct S has copy, drop {}

            public macro fun wrap(self: &S, value: u64): u64 {
                value
            }

            fun main(s: &S) {
                let wrapped = s.wrap!(1);
                wrapped;
              //^ u64
            }
        }
        """
    )

}
