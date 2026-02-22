package org.sui.lang.resolve

import org.sui.lang.core.psi.MvSpecFunction
import org.sui.lang.core.psi.MvSpecInlineFunction
import org.sui.lang.core.psi.module
import org.sui.utils.tests.MvTestBase

class FunctionLikeModuleResolutionTest : MvTestBase() {
    fun `test spec function module from module spec`() {
        InlineFile(
            """
            module 0x1::m {}
            spec 0x1::m {
                spec fun spec_now_microseconds(): u64 {
                        //^
                    1
                }
            }
            """
        )

        val specFunction = findElementInEditor<MvSpecFunction>()
        assertEquals("0x1::m", specFunction.module?.qualName?.editorText())
        assertEquals("0x1::m::spec_now_microseconds", specFunction.qualName?.editorText())
    }

    fun `test spec inline function module from item spec`() {
        InlineFile(
            """
            module 0x1::m {
                fun call() {}
                spec call {
                    fun helper(): num {
                       //^
                        1
                    }
                }
            }
            """
        )

        val inlineFunction = findElementInEditor<MvSpecInlineFunction>()
        assertEquals("0x1::m", inlineFunction.module?.qualName?.editorText())
    }

    fun `test spec inline function module from module item spec`() {
        InlineFile(
            """
            module 0x1::m {}
            spec 0x1::m {
                spec module {
                    fun helper(): num {
                       //^
                        1
                    }
                }
            }
            """
        )

        val inlineFunction = findElementInEditor<MvSpecInlineFunction>()
        assertEquals("0x1::m", inlineFunction.module?.qualName?.editorText())
    }
}
