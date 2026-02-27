package org.sui.ide.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import org.sui.cli.MoveProjectsService
import org.sui.stdext.toPathOrNull
import java.nio.file.Path

object MoveAnalyzerCommandProvider {
    private const val STDIO_ARG: String = "--stdio"
    private val LEGACY_STDIO_COMPAT_NAMES: Set<String> = setOf("sui-move-analyzer", "sui-move-analyzer.exe")

    fun createCommandLine(project: Project): GeneralCommandLine {
        val executable = MoveAnalyzerPathResolver.resolveExecutable(project)?.toString()
            ?: MoveAnalyzerPathResolver.defaultExecutableName()

        val commandLine = GeneralCommandLine(executable)
        val launchArgs = launchArguments(executable)
        if (launchArgs.isNotEmpty()) {
            commandLine.withParameters(launchArgs)
        }
        resolveWorkingDirectory(project)?.let { commandLine.withWorkDirectory(it.toString()) }
        return commandLine
    }

    internal fun launchArguments(executablePath: String): List<String> {
        val executableName = executablePath.toPathOrNull()
            ?.fileName
            ?.toString()
            ?.lowercase()
            ?: return listOf(STDIO_ARG)

        return if (executableName in LEGACY_STDIO_COMPAT_NAMES) {
            listOf(STDIO_ARG)
        } else {
            emptyList()
        }
    }

    private fun resolveWorkingDirectory(project: Project): Path? {
        val moveProjectsService = project.getService(MoveProjectsService::class.java)
        val moveProjectPath = moveProjectsService?.allProjects?.firstOrNull()?.contentRootPath
        return moveProjectPath ?: project.basePath?.toPathOrNull()
    }
}
