package org.sui.ide.lsp

import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.nio.file.Files
import java.nio.file.Path

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

    fun `test working directory falls back to project base when multiple move roots detected`() {
        val rootA = Files.createTempDirectory("move-project-a")
        val rootB = Files.createTempDirectory("move-project-b")
        val projectBase = Files.createTempDirectory("move-project-base")

        val resolved = MoveAnalyzerCommandProvider.resolveWorkingDirectory(
            moveProjectPaths = listOf(rootA, rootB),
            projectBasePath = projectBase,
        )

        check(resolved == projectBase) {
            "Expected project base path for multi-project setup. expected=$projectBase actual=$resolved"
        }
    }

    fun `test working directory falls back to common root when project base is invalid in multi-project setup`() {
        val workspaceRoot = Files.createTempDirectory("move-project-workspace")
        val rootA = Files.createDirectories(workspaceRoot.resolve("project-a"))
        val rootB = Files.createDirectories(workspaceRoot.resolve("project-b"))
        val missingProjectBase = workspaceRoot.resolve("missing-project-base")

        val resolved = MoveAnalyzerCommandProvider.resolveWorkingDirectory(
            moveProjectPaths = listOf(rootA, rootB),
            projectBasePath = missingProjectBase,
        )

        check(resolved == workspaceRoot) {
            "Expected existing common workspace root when project base is invalid. " +
                "expected=$workspaceRoot actual=$resolved"
        }
    }

    fun `test working directory falls back to project base when no move root detected`() {
        val projectBase = Files.createTempDirectory("move-project-base")

        val resolved = MoveAnalyzerCommandProvider.resolveWorkingDirectory(
            moveProjectPaths = emptyList(),
            projectBasePath = projectBase,
        )

        check(resolved == projectBase) {
            "Expected project base path when no move root exists. expected=$projectBase actual=$resolved"
        }
    }

    fun `test working directory prefers single move root`() {
        val rootA = Files.createTempDirectory("move-project-a")
        val projectBase = Files.createTempDirectory("move-project-base")

        val resolved = MoveAnalyzerCommandProvider.resolveWorkingDirectory(
            moveProjectPaths = listOf(rootA),
            projectBasePath = projectBase,
        )

        check(resolved == rootA) {
            "Expected single move root path. expected=$rootA actual=$resolved"
        }
    }

    private fun createExecutable(fileName: String): Path {
        val tempDir = Files.createTempDirectory("move-analyzer-command")
        val executable = tempDir.resolve(fileName)
        Files.writeString(executable, "#!/usr/bin/env bash\nexit 0\n")
        executable.toFile().setExecutable(true)
        return executable
    }
}
