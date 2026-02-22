package org.sui.lang.parser

import org.sui.ide.inspections.fixes.CompilerV2Feat.RESOURCE_CONTROL
import org.sui.utils.tests.CompilerV2Features
import org.sui.utils.tests.parser.MvParsingTestCase

class CompleteParsingTest : MvParsingTestCase("complete") {
    // Temporarily set the OVERWRITE_TESTDATA flag to update test data
    init {
        try {
            val field = Class.forName("com.intellij.testFramework.UsefulTestCase").getField("OVERWRITE_TESTDATA")
            field.isAccessible = true
            // Use reflection to set the value of the static field
            val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
            field.setBoolean(null, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun `test comments`() = doTest()
    fun `test addresses`() = doTest()
    fun `test attributes`() = doTest()

    // functions
    fun `test function declarations`() = doTest()
    fun `test function calls`() = doTest()

    // expressions
    fun `test strings`() = doTest()
    fun `test vectors`() = doTest()
    fun `test dot expressions`() = doTest()
    fun `test expressions`() = doTest()
    fun `test expressions assignments`() = doTest()
    fun `test expressions if else as`() = doTest()
    fun `test expressions angle brackets`() = doTest()
    fun `test expressions specs`() = doTest()

    // use
    fun `test use`() = doTest()
    fun `test use fun`() = doTest()
    fun `test friend`() = doTest()

    // assignments
    fun `test let patterns`() = doTest()
    fun `test assignments`() = doTest()

    // structs
    fun `test struct declarations`() = doTest()
    fun `test struct literals`() = doTest()
    fun `test tuple struct fields`() = doTest()

    // misc
    fun `test while loop inline assignment`() = doTest()
    fun `test contextual token operators`() = doTest()
    fun `test generics`() = doTest()
    fun `test annotated literals`() = doTest()

    fun `test macros`() = doTest()
    fun `test loops`() = doTest()
    fun `test break values`() = doTest()

    fun `test revised paths`() = doTest()

    fun `test paths`() = doTest()

    // compiler v2
    fun `test index expr`() = doTest()
    fun `test public package`() = doTest()
    fun `test enums`() = doTest()
    fun `test match`() = doTest()

    // feature declaration is required here, as it's a parser-level feature
    @CompilerV2Features(RESOURCE_CONTROL)
    fun `test access control`() = doTest()

    override fun doTest() {
        super.doTest(true, true)
    }
}
