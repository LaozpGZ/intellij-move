/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.sui.utils.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.enableInspectionTool
import org.intellij.lang.annotations.Language
import org.sui.cli.settings.moveSettings
import org.sui.cli.tests.NamedAddressService
import org.sui.cli.tests.NamedAddressServiceTestImpl
import org.sui.ide.inspections.fixes.CompilerV2Feat
import org.sui.ide.inspections.fixes.CompilerV2Feat.*
import org.sui.utils.tests.base.MvTestCase
import org.sui.utils.tests.base.TestCase
import org.sui.utils.tests.base.findElementsWithDataAndOffsetInEditor
import java.lang.annotation.Inherited
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DebugMode(val enabled: Boolean)

@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithEnabledInspections(vararg val inspections: KClass<out InspectionProfileEntry>)

@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CompilerV2Features(vararg val features: CompilerV2Feat)

@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedAddress(val name: String, val value: String)

fun UsefulTestCase.handleCompilerV2Annotations(project: Project) {
    val enabledCompilerV2 = this.findAnnotationInstance<CompilerV2Features>()
    // triggers projects refresh
    project.moveSettings.modifyTemporary(this.testRootDisposable) {
        // Disable all compiler v2 features by default.
        it.enableResourceAccessControl = false
        it.enableReceiverStyleFunctions = false
        it.enableIndexExpr = false
        it.enablePublicPackage = false

        if (enabledCompilerV2 != null) {
            for (feature in enabledCompilerV2.features) {
                when (feature) {
                    RESOURCE_CONTROL -> it.enableResourceAccessControl = true
                    RECEIVER_STYLE_FUNCTIONS -> it.enableReceiverStyleFunctions = true
                    INDEXING -> it.enableIndexExpr = true
                    PUBLIC_PACKAGE -> it.enablePublicPackage = true
                }
            }
        }
    }
}

fun UsefulTestCase.handleNamedAddressAnnotations(project: Project) {
    val namedAddresses = this.findAnnotationInstances<NamedAddress>()
    val namedAddressService = project.service<NamedAddressService>() as NamedAddressServiceTestImpl
    for (namedAddress in namedAddresses) {
        namedAddressService.namedAddresses[namedAddress.name] = namedAddress.value
    }
}

abstract class MvTestBase : MvLightTestBase(),
    MvTestCase {
    protected val fileName: String
        get() = "${getTestName(true)}.$testFileExtension"
    open val dataPath: String = ""

    override fun setUp() {
        super.setUp()

        setupInspections()

//        val isDebugMode = this.findAnnotationInstance<DebugMode>()?.enabled ?: true
//        setRegistryKey("org.move.debug.enabled", isDebugMode)
//
//        val isCompilerV2 = this.findAnnotationInstance<CompilerV2>() != null
//        project.moveSettings.modifyTemporary(testRootDisposable) {
//            it.isCompilerV2 = isCompilerV2
//        }
//
//        val blockchain = this.findAnnotationInstance<WithBlockchain>()?.blockchain ?: Blockchain.APTOS
//        // triggers projects refresh
//        project.moveSettings.modify {
//            it.blockchain = blockchain
//        }
    }

    private fun setupInspections() {
        for (inspection in findAnnotationInstance<WithEnabledInspections>()?.inspections.orEmpty()) {
            enableInspectionTool(project, inspection.createInstance(), testRootDisposable)
        }
    }

    override fun getTestDataPath(): String = "${TestCase.testResourcesPath}/$dataPath"
    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }

    protected fun InlineFile(@Language("Move") code: String, name: String = "main.move"): InlineFile {
        return InlineFile(myFixture, code, name)
    }

    protected inline fun <reified T : PsiElement> findElementInEditor(marker: String = "^"): T =
        findElementInEditor(T::class.java, marker)

    protected fun <T : PsiElement> findElementInEditor(psiClass: Class<T>, marker: String): T {
        val (element, data) = findElementWithDataAndOffsetInEditor(psiClass, marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    protected inline fun <reified T : PsiElement> findElementAndDataInEditor(marker: String = "^"): Pair<T, String> {
        val (element, data) = findElementWithDataAndOffsetInEditor<T>(marker)
        return element to data
    }

    protected inline fun <reified T : PsiElement> findElementAndOffsetInEditor(marker: String = "^"): Pair<T, Int> {
        val (element, _, offset) = findElementWithDataAndOffsetInEditor<T>(marker)
        return element to offset
    }

    protected inline fun <reified T : PsiElement> findElementWithDataAndOffsetInEditor(
        marker: String = "^"
    ): Triple<T, String, Int> {
        return findElementWithDataAndOffsetInEditor(T::class.java, marker)
    }

    protected fun <T : PsiElement> findElementWithDataAndOffsetInEditor(
        psiClass: Class<T>,
        marker: String
    ): Triple<T, String, Int> {
        val elementsWithDataAndOffset = myFixture.findElementsWithDataAndOffsetInEditor(psiClass, marker)
        check(elementsWithDataAndOffset.isNotEmpty()) { "No `$marker` marker:\n${myFixture.file.text}" }
        check(elementsWithDataAndOffset.size <= 1) { "More than one `$marker` marker:\n${myFixture.file.text}" }
        return elementsWithDataAndOffset.first()
    }

    protected fun checkByText(
        @Language("Move") before: String,
        @Language("Move") after: String,
        action: () -> Unit,
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val (before, after) = (fileName to fileName.replace(".move", "_after.move"))
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }

//    protected fun FileTree.prepareTestProjectFromFixture(): TestProject =
//        toTestProject(myFixture.project, myFixture.findFileInTempDir("."))

    protected open fun checkEditorAction(
        @Language("Move") before: String,
        @Language("Move") after: String,
        actionId: String,
        trimIndent: Boolean = true,
        callback: ((Editor, PsiFile) -> Unit)? = null,
    ) {
        fun String.trimIndentIfNeeded(): String = if (trimIndent) trimIndent() else this

        val beforeTrimmed = before.trimIndentIfNeeded()
        val afterTrimmed = after.trimIndentIfNeeded()

        checkByText(beforeTrimmed, afterTrimmed) {
            myFixture.performEditorAction(actionId)
            callback?.invoke(myFixture.editor, myFixture.file)

            // Print debug information
            val actualText = myFixture.file.text
            println("=== Actual text after optimization ===")
            println(actualText)
            println("\n=== Expected text ===")
            println(afterTrimmed)

            // Compare differences
            if (actualText != afterTrimmed) {
                println("\n=== Differences ===")
                val actualLines = actualText.lines()
                val expectedLines = afterTrimmed.lines()
                val maxLength = maxOf(actualLines.size, expectedLines.size)

                for (i in 0 until maxLength) {
                    val actualLine = actualLines.getOrElse(i) { "" }
                    val expectedLine = expectedLines.getOrElse(i) { "" }

                    if (actualLine != expectedLine) {
                        println("Line $i:")
                        println("  Actual:   '$actualLine'")
                        println("  Expected: '$expectedLine'")
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun checkHtmlStyle(html: String) {
            // http://stackoverflow.com/a/1732454
            val re = "<body>(.*)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val body = (re.find(html)?.let { it.groups[1]!!.value } ?: html).trim()
            check(body[0].isUpperCase()) {
                "Please start description with the capital latter"
            }

            check(body.last() == '.') {
                "Please end description with a period"
            }
        }

        @JvmStatic
        fun getResourceAsString(path: String): String? {
            val stream = MvTestBase::class.java.classLoader.getResourceAsStream(path)
                ?: return null

            return stream.bufferedReader().use { it.readText() }
        }
    }
}
