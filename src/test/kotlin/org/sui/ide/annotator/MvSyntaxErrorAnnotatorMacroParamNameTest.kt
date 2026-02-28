package org.sui.ide.annotator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MvSyntaxErrorAnnotatorMacroParamNameTest {
    private val annotator = MvSyntaxErrorAnnotator()
    private val isValidMacroParamNameMethod =
        MvSyntaxErrorAnnotator::class.java.getDeclaredMethod("isValidMacroParamName", String::class.java).apply {
            isAccessible = true
        }
    private val macroParamErrorMessageMethod =
        MvSyntaxErrorAnnotator::class.java.getDeclaredMethod(
            "macroParamErrorMessage",
            String::class.java,
            String::class.java
        ).apply { isAccessible = true }

    @Test
    fun `macro parameter names accept dollar prefixed and underscore`() {
        assertTrue(invokeIsValidMacroParamName("\$T"))
        assertTrue(invokeIsValidMacroParamName("\$value"))
        assertTrue(invokeIsValidMacroParamName("\$_"))
        assertTrue(invokeIsValidMacroParamName("_"))
    }

    @Test
    fun `macro parameter names reject non dollar and underscore prefix`() {
        assertFalse(invokeIsValidMacroParamName("T"))
        assertFalse(invokeIsValidMacroParamName("value"))
        assertFalse(invokeIsValidMacroParamName("_name"))
        assertFalse(invokeIsValidMacroParamName("\$"))
    }

    @Test
    fun `macro parameter error messages match expected wording`() {
        assertEquals(
            "Macro function type parameter must start with `$`",
            invokeMacroParamErrorMessage("type", "T")
        )
        assertEquals(
            "Macro function value parameter cannot start with '_'; use `\$_`",
            invokeMacroParamErrorMessage("value", "_value")
        )
    }

    private fun invokeIsValidMacroParamName(name: String): Boolean {
        return isValidMacroParamNameMethod.invoke(annotator, name) as Boolean
    }

    private fun invokeMacroParamErrorMessage(kind: String, name: String): String {
        return macroParamErrorMessageMethod.invoke(annotator, kind, name) as String
    }
}
