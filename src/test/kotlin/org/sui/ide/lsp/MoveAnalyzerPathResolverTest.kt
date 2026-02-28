package org.sui.ide.lsp

import org.sui.utils.tests.MvTestBase
import java.nio.file.Files
import java.nio.file.Path

class MoveAnalyzerPathResolverTest : MvTestBase() {
    fun `test resolve executable prefers configured executable path`() {
        val tempDir = Files.createTempDirectory("move-analyzer-resolver")
        val configuredExecutable = createExecutable(tempDir.resolve("custom-move-analyzer"))

        val resolved = MoveAnalyzerPathResolver.resolveExecutable(project, configuredExecutable.toString())

        check(resolved == configuredExecutable) {
            "Expected configured executable to win. expected=$configuredExecutable actual=$resolved"
        }
    }

    fun `test resolve detailed returns configured source`() {
        val tempDir = Files.createTempDirectory("move-analyzer-resolver")
        val configuredExecutable = createExecutable(tempDir.resolve("custom-move-analyzer"))
        val pathExecutable = createExecutable(tempDir.resolve("from-path"))
        val home = Files.createTempDirectory("move-analyzer-home")

        val result = MoveAnalyzerPathResolver.resolveDetailed(
            configuredPath = configuredExecutable.toString(),
            pathLookup = { pathExecutable },
            homeDir = home,
        )

        check(result.path == configuredExecutable) {
            "Expected configured path. expected=$configuredExecutable actual=${result.path}"
        }
        check(result.source == MoveAnalyzerPathResolver.ResolutionSource.CONFIGURED) {
            "Expected CONFIGURED source, got ${result.source}"
        }
    }

    fun `test resolve detailed returns PATH source`() {
        val tempDir = Files.createTempDirectory("move-analyzer-resolver")
        val pathExecutable = createExecutable(tempDir.resolve("sui-move-analyzer"))
        val home = Files.createTempDirectory("move-analyzer-home")

        val result = MoveAnalyzerPathResolver.resolveDetailed(
            configuredPath = null,
            pathLookup = { name -> if (name == "sui-move-analyzer") pathExecutable else null },
            homeDir = home,
        )

        check(result.path == pathExecutable) {
            "Expected PATH executable. expected=$pathExecutable actual=${result.path}"
        }
        check(result.source == MoveAnalyzerPathResolver.ResolutionSource.PATH) {
            "Expected PATH source, got ${result.source}"
        }
    }

    fun `test resolve detailed returns CARGO_HOME source`() {
        val home = Files.createTempDirectory("move-analyzer-home")
        val cargoExecutable = createExecutable(home.resolve(".cargo").resolve("bin").resolve("move-analyzer"))

        val result = MoveAnalyzerPathResolver.resolveDetailed(
            configuredPath = null,
            pathLookup = { null },
            homeDir = home,
        )

        check(result.path == cargoExecutable) {
            "Expected CARGO_HOME executable. expected=$cargoExecutable actual=${result.path}"
        }
        check(result.source == MoveAnalyzerPathResolver.ResolutionSource.CARGO_HOME) {
            "Expected CARGO_HOME source, got ${result.source}"
        }
    }

    fun `test resolve detailed returns SUI_HOME source`() {
        val home = Files.createTempDirectory("move-analyzer-home")
        val suiExecutable = createExecutable(home.resolve(".sui").resolve("bin").resolve("sui-move-analyzer"))

        val result = MoveAnalyzerPathResolver.resolveDetailed(
            configuredPath = null,
            pathLookup = { null },
            homeDir = home,
        )

        check(result.path == suiExecutable) {
            "Expected SUI_HOME executable. expected=$suiExecutable actual=${result.path}"
        }
        check(result.source == MoveAnalyzerPathResolver.ResolutionSource.SUI_HOME) {
            "Expected SUI_HOME source, got ${result.source}"
        }
    }

    fun `test resolve detailed returns unresolved when no executable candidates`() {
        val home = Files.createTempDirectory("move-analyzer-home")

        val result = MoveAnalyzerPathResolver.resolveDetailed(
            configuredPath = null,
            pathLookup = { null },
            homeDir = home,
        )

        check(result.path == null) { "Expected null executable path, got ${result.path}" }
        check(result.source == MoveAnalyzerPathResolver.ResolutionSource.UNRESOLVED) {
            "Expected UNRESOLVED source, got ${result.source}"
        }
    }

    fun `test default executable name is sui move analyzer`() {
        check(MoveAnalyzerPathResolver.defaultExecutableName() == "sui-move-analyzer")
    }

    private fun createExecutable(path: Path): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, "#!/usr/bin/env bash\nexit 0\n")
        path.toFile().setExecutable(true)
        return path
    }
}
