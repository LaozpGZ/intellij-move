package org.sui.ide.annotator.syntaxErrors.compilerV2

import org.sui.ide.annotator.MvSyntaxErrorAnnotator
import org.sui.ide.inspections.fixes.CompilerV2Feat.PUBLIC_PACKAGE
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.annotation.AnnotatorTestCase

class FriendVisibilityMove2024Test : AnnotatorTestCase(MvSyntaxErrorAnnotator::class) {
    @CompilerV2Features(PUBLIC_PACKAGE)
    fun `test friend declaration is not supported in compiler v2`() = checkWarnings(
        """
        module 0x1::m {
            <error descr="friend is not supported in Move 2024">friend 0x1::a;</error>
            public(package) fun call() {}
        }
    """
    )
}
