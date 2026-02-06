package org.sui.lang.completion

import org.sui.utils.tests.completion.CompletionTestCase

class MacroCompletionTest : CompletionTestCase() {
    fun `test stdlib macros are not suggested outside macro call`() = checkNotContainsCompletion(
        "all!",
        """
        module 0x1::M {
            fun main(v: &vector<u8>) {
                al/*caret*/
            }
        }
        """
    )

    fun `test always visible stdlib macro is suggested outside macro call`() = checkContainsCompletion(
        "assert_eq!",
        """
        module 0x1::M {
            fun main() {
                ass/*caret*/
            }
        }
        """
    )

    fun `test stdlib macro is suggested in macro call context`() = checkContainsCompletion(
        "all!",
        """
        module 0x1::M {
            fun main(v: &vector<u8>) {
                a/*caret*/!()
            }
        }
        """
    )

    fun `test user macro shadows builtin macro completion`() = checkNotContainsCompletion(
        "assert!",
        """
        module 0x1::M {
            macro fun assert(a: bool, b: u64): bool { true }

            fun main() {
                ass/*caret*/
            }
        }
        """
    )
}
