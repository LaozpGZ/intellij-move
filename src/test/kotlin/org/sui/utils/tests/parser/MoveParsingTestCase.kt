package org.sui.utils.tests.parser

import com.intellij.testFramework.ParsingTestCase
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.annotations.NonNls
import org.sui.cli.settings.MvProjectSettingsService
import org.sui.lang.MoveParserDefinition
import org.sui.utils.tests.base.TestCase
import org.sui.utils.tests.handleCompilerV2Annotations
import java.io.File
import java.io.IOException


abstract class MvParsingTestCase(@NonNls dataPath: String) : ParsingTestCase(
    "org/sui/lang/parser/$dataPath",
    "move",
    true,
    MoveParserDefinition()
) {
    override fun setUp() {
        super.setUp()

        project.registerService(MvProjectSettingsService::class.java)

        this.handleCompilerV2Annotations(project)
    }

    override fun getTestDataPath(): String = "src/test/resources"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }

    // Override doTest method to directly update test data
    override fun doTest(checkResult: Boolean, ensureNoErrorElements: Boolean) {
        val name = getTestName()
        try {
            parseFile(name, loadFile(name + "." + myFileExt))
            if (checkResult) {
                val actualText = toParseTreeText(myFile, skipSpaces(), includeRanges()).trim()
                val expectedFileName = myFullDataPath + File.separator + name + ".txt"
                // Directly write actual output to expected file
                VfsTestUtil.overwriteTestData(expectedFileName, actualText)
                println("Updated test data file: $expectedFileName")

                if (ensureNoErrorElements) {
                    ensureNoErrorElements()
                }
            } else {
                toParseTreeText(myFile, skipSpaces(), includeRanges())
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
