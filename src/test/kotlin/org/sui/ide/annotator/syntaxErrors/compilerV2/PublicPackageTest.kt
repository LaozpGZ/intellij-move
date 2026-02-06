package org.sui.ide.annotator.syntaxErrors.compilerV2

import org.sui.ide.annotator.MvSyntaxErrorAnnotator
import org.sui.ide.inspections.fixes.CompilerV2Feat.PUBLIC_PACKAGE
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.annotation.AnnotatorTestCase

class PublicPackageTest : AnnotatorTestCase(MvSyntaxErrorAnnotator::class) {
    @CompilerV2Features(PUBLIC_PACKAGE)
    fun `test no error with compiler v2 public package`() = checkWarnings(
        """
        module 0x1::m {
            public(package) fun call() {}
        }        
    """
    )

    @CompilerV2Features(PUBLIC_PACKAGE)
    fun `test public friend is not supported in compiler v2`() = checkWarnings(
        """
        module 0x1::m {
            <error descr="public(friend) is not supported in Move 2024">public(friend)</error> fun call() {}
        }        
    """
    )

    @CompilerV2Features(PUBLIC_PACKAGE)
    fun `test cannot use public package together with public friend`() = checkWarnings(
        """
        module 0x1::m {
            <error descr="public(friend) is not supported in Move 2024">public(friend)</error> fun call1() {}
            public(package) fun call2() {}
                         
        }        
    """
    )

    @CompilerV2Features()
    fun `test cannot use public package in compiler v1`() = checkWarnings(
        """
        module 0x1::m {
            <error descr="public(package) is not supported in Aptos Move V1">public(package)</error> fun call() {}
                         
        }        
    """
    )
}
