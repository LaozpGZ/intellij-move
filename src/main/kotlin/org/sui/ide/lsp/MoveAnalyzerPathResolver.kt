package org.sui.ide.lsp

import com.intellij.openapi.project.Project
import org.sui.cli.settings.isValidExecutable
import org.sui.cli.settings.moveSettings
import org.sui.stdext.blankToNull
import org.sui.stdext.executableName
import org.sui.stdext.getCliFromPATH
import org.sui.stdext.toPathOrNull
import java.nio.file.Path

object MoveAnalyzerPathResolver {
    private const val PRIMARY_EXECUTABLE: String = "sui-move-analyzer"
    private const val FALLBACK_EXECUTABLE: String = "move-analyzer"

    fun resolveExecutable(project: Project): Path? {
        return resolveExecutable(project, project.moveSettings.moveAnalyzerPath)
    }

    fun resolveExecutable(project: Project, configuredPath: String?): Path? {
        return configuredPath.configuredExecutableOrNull()
            ?: resolveFromPath()
            ?: resolveFromCommonLocations()
    }

    fun defaultExecutableName(): String = PRIMARY_EXECUTABLE

    private fun resolveFromPath(): Path? {
        return getCliFromPATH(PRIMARY_EXECUTABLE)
            ?.takeIf { it.isValidExecutable() }
            ?: getCliFromPATH(FALLBACK_EXECUTABLE)
                ?.takeIf { it.isValidExecutable() }
    }

    private fun resolveFromCommonLocations(): Path? {
        val home = System.getProperty("user.home")?.toPathOrNull() ?: return null
        val candidates = listOf(
            home.resolve(".cargo").resolve("bin").resolve(executableName(PRIMARY_EXECUTABLE)),
            home.resolve(".cargo").resolve("bin").resolve(executableName(FALLBACK_EXECUTABLE)),
            home.resolve(".sui").resolve("bin").resolve(executableName(PRIMARY_EXECUTABLE)),
            home.resolve(".sui").resolve("bin").resolve(executableName(FALLBACK_EXECUTABLE)),
        )
        return candidates.firstOrNull { it.isValidExecutable() }
    }

    private fun String?.configuredExecutableOrNull(): Path? {
        return this
            ?.blankToNull()
            ?.toPathOrNull()
            ?.takeIf { it.isValidExecutable() }
    }
}
