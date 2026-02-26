package org.sui.ide.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import org.sui.cli.MoveProjectsService
import org.sui.stdext.toPathOrNull
import java.nio.file.Path

object MoveAnalyzerCommandProvider {
    private const val STDIO_ARG: String = "--stdio"

    fun createCommandLine(project: Project): GeneralCommandLine {
        val executable = MoveAnalyzerPathResolver.resolveExecutable(project)?.toString()
            ?: MoveAnalyzerPathResolver.defaultExecutableName()

        val commandLine = GeneralCommandLine(executable, STDIO_ARG)
        resolveWorkingDirectory(project)?.let { commandLine.withWorkDirectory(it.toString()) }
        return commandLine
    }

    private fun resolveWorkingDirectory(project: Project): Path? {
        val moveProjectsService = project.getService(MoveProjectsService::class.java)
        val moveProjectPath = moveProjectsService?.allProjects?.firstOrNull()?.contentRootPath
        return moveProjectPath ?: project.basePath?.toPathOrNull()
    }
}
