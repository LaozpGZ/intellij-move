package org.sui.ide.annotator.errors

import org.sui.ide.annotator.MvErrorAnnotator
import org.sui.utils.tests.annotation.AnnotatorTestCase

class MatchArmDuplicateErrorTest : AnnotatorTestCase(MvErrorAnnotator::class) {
    fun `test duplicate enum variant in match arm`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A => 1,
                    <error descr="Duplicate match arm for `E::A`">E::A</error> => 2,
                    E::B => 3,
                }
            }
        }
        """
    )

    fun `test no duplicate error for guarded arm`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A if true => 1,
                    E::A => 2,
                    E::B => 3,
                }
            }
        }
        """
    )
}
