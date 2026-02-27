package org.sui.ide.lsp

import org.sui.utils.tests.MvTestBase
import java.nio.file.Files

class MoveAnalyzerPathResolverTest : MvTestBase() {
    fun `test resolve executable prefers configured executable path`() {
        val tempDir = Files.createTempDirectory("move-analyzer-resolver")
        val configuredExecutable = createExecutable(tempDir.resolve("custom-move-analyzer"))

        val resolved = MoveAnalyzerPathResolver.resolveExecutable(project, configuredExecutable.toString())

        check(resolved == configuredExecutable) {
            "Expected configured executable to win. expected=$configuredExecutable actual=$resolved"
        }
    }

    fun `test default executable name is sui move analyzer`() {
        check(MoveAnalyzerPathResolver.defaultExecutableName() == "sui-move-analyzer")
    }

    private fun createExecutable(path: java.nio.file.Path): java.nio.file.Path {
        Files.writeString(path, "#!/usr/bin/env bash\nexit 0\n")
        path.toFile().setExecutable(true)
        return path
    }
}

