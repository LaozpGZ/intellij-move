package org.sui.utils.tests

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jdom.Element
import org.sui.cli.runConfigurations.aptos.cmd.AptosCommandConfiguration
import org.sui.lang.core.psi.ext.ancestorOrSelf
import org.sui.openapiext.toXmlString
import org.sui.utils.tests.base.TestCase
import com.intellij.openapi.util.io.FileUtil
import java.io.File

abstract class RunConfigurationProducerTestBase(val testDir: String) : MvProjectTestBase() {
    protected fun checkOnFsItem(fsItem: PsiFileSystemItem) {
        val configurationContext = ConfigurationContext(fsItem)
        check(configurationContext)
    }

    protected fun checkNoConfigurationOnFsItem(fsItem: PsiFileSystemItem) {
        val configurationContext = ConfigurationContext(fsItem)
        val configurations = configurationContext.configurationsFromContext.orEmpty()
        check(configurations.isEmpty()) { "Found unexpected run configurations" }
    }

    protected inline fun <reified T : PsiElement> checkOnElement() {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
            ?.ancestorOrSelf<T>()
            ?: error("Failed to find element of `${T::class.simpleName}` class at caret")
        val configurationContext = ConfigurationContext(element)
        check(configurationContext)
    }

    protected inline fun <reified T : PsiElement> checkNoConfigurationOnElement() {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
            ?.ancestorOrSelf<T>()
            ?: error("Failed to find element of `${T::class.simpleName}` class at caret")
        val configurationContext = ConfigurationContext(element)
        val configurations = configurationContext.configurationsFromContext.orEmpty()
        check(configurations.isEmpty()) { "Found unexpected run configurations" }
    }

    protected fun check(configurationContext: ConfigurationContext) {
        val configurations =
            configurationContext.configurationsFromContext.orEmpty().map { it.configurationSettings }
        check(configurations.isNotEmpty()) { "No configurations found" }

        val rootDirectory = this.rootDirectory ?: error("testProject is not initialized yet")
        val testId = rootDirectory.name

        val root = Element("configurations")
        configurations.forEach {
            val confSettings = it as RunnerAndConfigurationSettingsImpl
            val content = confSettings.writeScheme()

            val workingDirectoryChild =
                content.children.find { c -> c.getAttribute("name")?.value == "workingDirectory" }
            if (workingDirectoryChild != null) {
                val workingDirectory = workingDirectoryChild.getAttribute("value")
                if (workingDirectory != null) {
                    workingDirectory.value = workingDirectory.value
                        .replace("file://\$USER_HOME\$", "file://")
                        .replace("file://\$PROJECT_DIR\$/../$testId", "file://")
                }
            }
            root.addContent(content)
        }
        val transformedXml = root.toXmlString().replace(testId, "unitTest_ID")

        val testDataPath = "${TestCase.testResourcesPath}/org/sui/cli/producers.fixtures/$testDir"
        // Write raw XML to file
        File("/tmp/raw_transformed_xml.txt").writeText(transformedXml)

        val expectedFile = File("$testDataPath/${getTestName(true)}.xml")
        val expected = FileUtil.loadFile(expectedFile, true)
            .replace("<envs />", "") // Remove empty envs tag with space
            .replace("<envs/>", "") // Remove empty envs tag without space
            .replace("\\s+".toRegex(), " ").trim()
            .replace("\\s*/>".toRegex(), "/>") // Normalize self-closing tags

        val actual = transformedXml
            .replace("<envs />", "") // Remove empty envs tag with space
            .replace("<envs/>", "") // Remove empty envs tag without space
            .replace("\\s+".toRegex(), " ").trim()
            .replace("\\s*/>".toRegex(), "/>") // Normalize self-closing tags

        // Write to a file for debugging
        val outputFile = File("/tmp/test_output_${getTestName(true)}.txt")
        outputFile.writeText(
            "=== Test: ${getTestName(true)} ===\n" +
            "=== Actual XML ===\n$actual\n" +
            "=== Expected XML ===\n$expected\n"
        )

        if (actual != expected) {
            val diff = buildString {
                append("=== Differences ===\n")
                val minLength = minOf(actual.length, expected.length)
                var i = 0
                while (i < minLength) {
                    if (actual[i] != expected[i]) {
                        val actualSnippet = actual.substring(i, minOf(i + 20, actual.length))
                        val expectedSnippet = expected.substring(i, minOf(i + 20, expected.length))
                        append("Difference at position $i:\n")
                        append("Actual: '$actualSnippet'\n")
                        append("Expected: '$expectedSnippet'\n")
                        break
                    }
                    i++
                }
                if (actual.length != expected.length) {
                    append("Length differs: actual=${actual.length}, expected=${expected.length}\n")
                }
            }
            outputFile.appendText(diff)
        }

        check(actual == expected) { "XML content does not match" }
    }

    protected fun doTestRemembersContext(
        producer: RunConfigurationProducer<AptosCommandConfiguration>,
        ctx1: PsiElement,
        ctx2: PsiElement
    ) {
        val contexts = listOf(ConfigurationContext(ctx1), ConfigurationContext(ctx2))
        val configsFromContext = contexts.map { it.configurationsFromContext!!.single() }
        configsFromContext.forEach { check(it.isProducedBy(producer.javaClass)) }
        val configs = configsFromContext.map { it.configuration as AptosCommandConfiguration }
        for (i in 0..1) {
            check(producer.isConfigurationFromContext(configs[i], contexts[i])) {
                "Configuration created from context does not believe it"
            }

            check(!producer.isConfigurationFromContext(configs[i], contexts[1 - i])) {
                "Configuration wrongly believes it is from another context"
            }
        }
    }
}
