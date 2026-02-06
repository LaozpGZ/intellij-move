package org.sui.ide.annotator.errors

import org.sui.ide.annotator.MvErrorAnnotator
import org.sui.ide.inspections.fixes.CompilerV2Feat.LET_MUT_REQUIRED
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.annotation.AnnotatorTestCase

class LetMutRequiredErrorTest : AnnotatorTestCase(MvErrorAnnotator::class) {
    @CompilerV2Features(LET_MUT_REQUIRED)
    fun `test mutable binding required for assignment in move 2024`() = checkErrors(
        """
        module 0x1::m {
            fun main() {
                let value = 1;
                <error descr="Mutable binding is required for assignment in Move 2024">value</error> = 2;
            }
        }
        """
    )

    @CompilerV2Features(LET_MUT_REQUIRED)
    fun `test mutable binding required for mut borrow in move 2024`() = checkErrors(
        """
        module 0x1::m {
            struct S has copy, drop { value: u64 }

            fun main() {
                let s = S { value: 1 };
                let _ref = &mut <error descr="Mutable binding is required for &mut borrow in Move 2024">s</error>;
            }
        }
        """
    )

    @CompilerV2Features(LET_MUT_REQUIRED)
    fun `test no error for mut binding in move 2024`() = checkErrors(
        """
        module 0x1::m {
            fun main() {
                let mut value = 1;
                value = 2;
            }
        }
        """
    )

    @CompilerV2Features()
    fun `test no error for assignment without mut in move v1`() = checkErrors(
        """
        module 0x1::m {
            fun main() {
                let value = 1;
                value = 2;
            }
        }
        """
    )
}
