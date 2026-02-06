package org.sui.ide.annotator.syntaxErrors.compilerV2

import org.sui.ide.annotator.MvSyntaxErrorAnnotator
import org.sui.ide.inspections.fixes.CompilerV2Feat.PUBLIC_STRUCT_REQUIRED
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.annotation.AnnotatorTestCase

class PublicStructMove2024Test : AnnotatorTestCase(MvSyntaxErrorAnnotator::class) {
    @CompilerV2Features(PUBLIC_STRUCT_REQUIRED)
    fun `test public struct is required in move 2024`() = checkWarnings(
        """
        module 0x1::m {
            <error descr="public struct is required in Move 2024">struct</error> S {}
        }
        """
    )

    @CompilerV2Features(PUBLIC_STRUCT_REQUIRED)
    fun `test no error for public struct in move 2024`() = checkWarnings(
        """
        module 0x1::m {
            public struct S {}
        }
        """
    )

    @CompilerV2Features()
    fun `test no error for private struct in move v1`() = checkWarnings(
        """
        module 0x1::m {
            struct S {}
        }
        """
    )
}
