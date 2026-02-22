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

    fun `test guarded enum arm is unreachable after unguarded same variant`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A => 1,
                    <error descr="Unreachable match arm">E::A</error> if true => 2,
                    E::B => 3,
                }
            }
        }
        """
    )

    fun `test non exhaustive match reports missing arms`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) <error descr="Non-exhaustive match. Missing arms: E::B">{
                    E::A => 1,
                }</error>
            }
        }
        """
    )

    fun `test guarded enum arm does not make match exhaustive`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) <error descr="Non-exhaustive match. Missing arms: E::A">{
                    E::A if true => 1,
                    E::B => 2,
                }</error>
            }
        }
        """
    )

    fun `test unreachable match arm after wildcard`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    _ => 1,
                    <error descr="Unreachable match arm">E::A</error> => 2,
                }
            }
        }
        """
    )

    fun `test wildcard arm after guarded wildcard is reachable`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    _ if true => 1,
                    _ => 2,
                }
            }
        }
        """
    )

    fun `test guarded wildcard arm is unreachable after wildcard`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    _ => 1,
                    <error descr="Unreachable match arm">_</error> if true => 2,
                }
            }
        }
        """
    )

    fun `test no unreachable error for guarded wildcard arm`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    _ if true => 1,
                    E::A => 2,
                    E::B => 3,
                }
            }
        }
        """
    )

    fun `test unreachable match arm after all enum variants are covered`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A => 1,
                    E::B => 2,
                    <error descr="Duplicate match arm for `E::A`"><error descr="Unreachable match arm">E::A</error></error> => 3,
                }
            }
        }
        """
    )

    fun `test wildcard arm is unreachable if all enum variants already covered`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A => 1,
                    E::B => 2,
                    <error descr="Unreachable match arm">_</error> => 3,
                }
            }
        }
        """
    )

    fun `test all variants guarded without catch-all is non-exhaustive`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) <error descr="Non-exhaustive match. Missing arms: E::A, E::B">{
                    E::A if true => 1,
                    E::B if true => 2,
                }</error>
            }
        }
        """
    )

    fun `test mixed guarded and unguarded arms partial coverage is non-exhaustive`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B, C }

            fun main(e: E): u8 {
                match (e) <error descr="Non-exhaustive match. Missing arms: E::B">{
                    E::A => 1,
                    E::B if true => 2,
                    E::C => 3,
                }</error>
            }
        }
        """
    )

    fun `test guarded wildcard followed by unguarded wildcard is exhaustive`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    _ if true => 1,
                    _ => 2,
                }
            }
        }
        """
    )

    fun `test guarded wildcard alone is non-exhaustive`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) <error descr="Non-exhaustive match. Missing arms: E::A, E::B">{
                    _ if true => 1,
                }</error>
            }
        }
        """
    )

    fun `test mixed guarded and unguarded arms full coverage is exhaustive`() = checkErrors(
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

    fun `test multiple guarded arms for same variant before unguarded`() = checkErrors(
        """
        module 0x1::m {
            enum E has copy, drop { A, B }

            fun main(e: E): u8 {
                match (e) {
                    E::A if true => 1,
                    E::A if true => 2,
                    E::A => 3,
                    E::B => 4,
                }
            }
        }
        """
    )
}
