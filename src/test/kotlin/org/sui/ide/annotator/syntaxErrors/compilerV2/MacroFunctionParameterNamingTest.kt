package org.sui.ide.annotator.syntaxErrors.compilerV2

import org.sui.ide.annotator.MvSyntaxErrorAnnotator
import org.sui.ide.inspections.fixes.CompilerV2Feat.MACRO_FUNCTIONS
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.annotation.AnnotatorTestCase

class MacroFunctionParameterNamingTest : AnnotatorTestCase(MvSyntaxErrorAnnotator::class) {
    @CompilerV2Features(MACRO_FUNCTIONS)
    fun `test macro function type and value params require dollar prefix`() = checkWarnings(
        """
        module 0x1::m {
            public macro fun bad<
                <error descr="Macro function type parameter must start with `${'$'}`">T</error>
            >(
                <error descr="Macro function value parameter must start with `${'$'}`">x</error>: u8
            ): u8 {
                x
            }
        }
        """
    )

    @CompilerV2Features(MACRO_FUNCTIONS)
    fun `test macro function params cannot start with underscore`() = checkWarnings(
        """
        module 0x1::m {
            public macro fun bad<
                <error descr="Macro function type parameter cannot start with '_'; use `${'$'}_`">_T</error>
            >(
                <error descr="Macro function value parameter cannot start with '_'; use `${'$'}_`">_x</error>: u8
            ): u8 {
                _x
            }
        }
        """
    )

    @CompilerV2Features(MACRO_FUNCTIONS)
    fun `test macro function params allow dollar prefixed names and underscore`() = checkWarnings(
        """
        module 0x1::m {
            public macro fun ok<${'$'}T>(${'$'}x: ${'$'}T, _: u8, ${'$'}_: u64): ${'$'}T {
                ${'$'}x
            }
        }
        """
    )
}
