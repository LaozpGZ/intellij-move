package org.sui.ide.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.sui.cli.MoveProjectsService
import org.sui.openapiext.common.isUnitTestMode
import org.sui.stdext.toPathOrNull
import java.nio.file.Files
import java.nio.file.Path

object MoveAnalyzerCommandProvider {
    private const val STDIO_ARG: String = "--stdio"
    private val LEGACY_STDIO_COMPAT_NAMES: Set<String> = setOf("sui-move-analyzer", "sui-move-analyzer.exe")
    private val LOG = logger<MoveAnalyzerCommandProvider>()

    fun createCommandLine(project: Project): GeneralCommandLine {
        val resolution = MoveAnalyzerPathResolver.resolveDetailed(project)
        val executable = resolution.path?.toString()
            ?: MoveAnalyzerPathResolver.defaultExecutableName()

        val commandLine = GeneralCommandLine(executable)
        val launchArgs = launchArguments(executable)
        if (launchArgs.isNotEmpty()) {
            commandLine.withParameters(launchArgs)
        }
        val workDir = resolveWorkingDirectory(project)
        workDir?.let { commandLine.withWorkDirectory(it.toString()) }
        logCommandDecisionIfNeeded(
            executable = executable,
            launchArgs = launchArgs,
            workDir = workDir,
            resolutionSource = resolution.source,
        )
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
        val moveProjectPaths = moveProjectsService?.allProjects.orEmpty()
            .mapNotNull { it.contentRootPath }
        val projectBasePath = project.basePath?.toPathOrNull()
        return resolveWorkingDirectory(moveProjectPaths, projectBasePath)
    }

    internal fun resolveWorkingDirectory(moveProjectPaths: List<Path>, projectBasePath: Path?): Path? {
        val uniqueProjectPaths = moveProjectPaths.distinct()
            .map { it.normalize() }
            .filter { Files.isDirectory(it) }
        val normalizedProjectBasePath = projectBasePath?.normalize()?.takeIf { Files.isDirectory(it) }
        return when {
            uniqueProjectPaths.size == 1 -> uniqueProjectPaths.single()
            uniqueProjectPaths.size > 1 -> normalizedProjectBasePath
                ?: findCommonAncestor(uniqueProjectPaths)
                ?: uniqueProjectPaths.firstOrNull()
            else -> normalizedProjectBasePath
        }
    }

    private fun findCommonAncestor(paths: List<Path>): Path? {
        if (paths.isEmpty()) return null
        var common = paths.first().normalize()
        for (path in paths.drop(1)) {
            val normalized = path.normalize()
            while (!normalized.startsWith(common)) {
                common = common.parent ?: return null
            }
        }
        return common
    }

    private fun logCommandDecisionIfNeeded(
        executable: String,
        launchArgs: List<String>,
        workDir: Path?,
        resolutionSource: MoveAnalyzerPathResolver.ResolutionSource,
    ) {
        if (isUnitTestMode) return
        LOG.info(
            "move-analyzer command: executable=$executable, args=$launchArgs, " +
                "workDir=${workDir ?: "<null>"}, source=$resolutionSource"
        )
    }
}
