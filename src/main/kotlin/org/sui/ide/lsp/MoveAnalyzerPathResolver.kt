package org.sui.ide.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.sui.cli.settings.isValidExecutable
import org.sui.cli.settings.moveSettings
import org.sui.openapiext.common.isUnitTestMode
import org.sui.stdext.blankToNull
import org.sui.stdext.executableName
import org.sui.stdext.getCliFromPATH
import org.sui.stdext.toPathOrNull
import java.nio.file.Path

object MoveAnalyzerPathResolver {
    private const val PRIMARY_EXECUTABLE: String = "sui-move-analyzer"
    private const val FALLBACK_EXECUTABLE: String = "move-analyzer"
    private val LOG = logger<MoveAnalyzerPathResolver>()

    @Volatile
    private var lastLoggedResolution: String? = null

    data class ResolutionResult(
        val path: Path?,
        val source: ResolutionSource,
    )

    enum class ResolutionSource {
        CONFIGURED,
        PATH,
        CARGO_HOME,
        SUI_HOME,
        UNRESOLVED,
    }

    fun resolveExecutable(project: Project): Path? {
        val configuredPath = project.moveSettings.moveAnalyzerPath
        val resolution = resolveDetailed(
            configuredPath,
            pathLookup = ::getCliFromPATH,
            homeDir = System.getProperty("user.home")?.toPathOrNull(),
        )
        logResolutionIfNeeded(configuredPath, resolution)
        return resolution.path
    }

    fun resolveExecutable(project: Project, configuredPath: String?): Path? {
        return resolveDetailed(project, configuredPath).path
    }

    fun resolveDetailed(project: Project): ResolutionResult {
        val configuredPath = project.moveSettings.moveAnalyzerPath
        val resolution = resolveDetailed(project, configuredPath)
        logResolutionIfNeeded(configuredPath, resolution)
        return resolution
    }

    fun resolveDetailed(project: Project, configuredPath: String?): ResolutionResult {
        return resolveDetailed(
            configuredPath = configuredPath,
            pathLookup = ::getCliFromPATH,
            homeDir = System.getProperty("user.home")?.toPathOrNull(),
        )
    }

    internal fun resolveDetailed(
        configuredPath: String?,
        pathLookup: (String) -> Path?,
        homeDir: Path?,
    ): ResolutionResult {
        configuredPath.configuredExecutableOrNull()?.let {
            return ResolutionResult(it, ResolutionSource.CONFIGURED)
        }
        resolveFromPath(pathLookup)?.let { return it }
        resolveFromCommonLocations(homeDir)?.let { return it }
        return ResolutionResult(path = null, source = ResolutionSource.UNRESOLVED)
    }

    fun defaultExecutableName(): String = PRIMARY_EXECUTABLE

    private fun resolveFromPath(pathLookup: (String) -> Path?): ResolutionResult? {
        return pathLookup(PRIMARY_EXECUTABLE)
            ?.takeIf { it.isValidExecutable() }
            ?.let { ResolutionResult(it, ResolutionSource.PATH) }
            ?: pathLookup(FALLBACK_EXECUTABLE)
                ?.takeIf { it.isValidExecutable() }
                ?.let { ResolutionResult(it, ResolutionSource.PATH) }
    }

    private fun resolveFromCommonLocations(homeDir: Path?): ResolutionResult? {
        val home = homeDir ?: return null
        val candidates = listOf(
            home.resolve(".cargo").resolve("bin").resolve(executableName(PRIMARY_EXECUTABLE)) to ResolutionSource.CARGO_HOME,
            home.resolve(".cargo").resolve("bin").resolve(executableName(FALLBACK_EXECUTABLE)) to ResolutionSource.CARGO_HOME,
            home.resolve(".sui").resolve("bin").resolve(executableName(PRIMARY_EXECUTABLE)) to ResolutionSource.SUI_HOME,
            home.resolve(".sui").resolve("bin").resolve(executableName(FALLBACK_EXECUTABLE)) to ResolutionSource.SUI_HOME,
        )
        return candidates.firstOrNull { (path, _) -> path.isValidExecutable() }
            ?.let { (path, source) -> ResolutionResult(path, source) }
    }

    private fun String?.configuredExecutableOrNull(): Path? {
        return this
            ?.blankToNull()
            ?.toPathOrNull()
            ?.takeIf { it.isValidExecutable() }
    }

    private fun logResolutionIfNeeded(configuredPath: String?, resolution: ResolutionResult) {
        if (isUnitTestMode) return

        val currentLogState = buildString {
            append("configured=")
            append(configuredPath?.ifBlank { "<blank>" } ?: "<null>")
            append(",source=")
            append(resolution.source.name)
            append(",path=")
            append(resolution.path?.toString() ?: "<null>")
        }
        if (lastLoggedResolution == currentLogState) return
        lastLoggedResolution = currentLogState
        LOG.info("move-analyzer resolution: $currentLogState")
    }
}
