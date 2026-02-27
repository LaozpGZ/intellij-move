package org.sui.ide.lsp

import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.nio.file.Files

class MoveAnalyzerCommandProviderTest : MvProjectTestBase() {
    fun `test command line uses configured executable and stdio argument`() {
        val executable = createExecutable("sui-move-analyzer")
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerPath = executable.toString()
        }
        testProject {
            namedMoveToml("SuiPackage")
            sources { main("/*caret*/") }
        }

        val commandLine = MoveAnalyzerCommandProvider.createCommandLine(project)

        check(commandLine.exePath == executable.toString()) {
            "Unexpected executable path: ${commandLine.exePath}"
        }
        check(commandLine.parametersList.parameters == listOf("--stdio")) {
            "Expected only --stdio argument, got ${commandLine.parametersList.parameters}"
        }
    }

    fun `test command line omits stdio argument for official move analyzer`() {
        val executable = createExecutable("move-analyzer")
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerPath = executable.toString()
        }
        testProject {
            namedMoveToml("SuiPackage")
            sources { main("/*caret*/") }
        }

        val commandLine = MoveAnalyzerCommandProvider.createCommandLine(project)

        check(commandLine.exePath == executable.toString()) {
            "Unexpected executable path: ${commandLine.exePath}"
        }
        check(commandLine.parametersList.parameters.isEmpty()) {
            "Expected no arguments for official move-analyzer, got ${commandLine.parametersList.parameters}"
        }
    }

    fun `test command line uses move project root as working directory`() {
        val executable = createExecutable("sui-move-analyzer")
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerPath = executable.toString()
        }
        val testProject = testProject {
            namedMoveToml("SuiPackage")
            sources {
                main("/*caret*/")
            }
        }

        val commandLine = MoveAnalyzerCommandProvider.createCommandLine(project)

        check(commandLine.workDirectory?.path == testProject.rootDirectory.path) {
            "Expected work dir ${testProject.rootDirectory.path}, got ${commandLine.workDirectory?.path}"
        }
    }

    private fun createExecutable(fileName: String): java.nio.file.Path {
        val tempDir = Files.createTempDirectory("move-analyzer-command")
        val executable = tempDir.resolve(fileName)
        Files.writeString(executable, "#!/usr/bin/env bash\nexit 0\n")
        executable.toFile().setExecutable(true)
        return executable
    }
}
