package org.sui.cli.manifest

import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

sealed class TomlDependency {
    abstract val name: String

    abstract fun localPath(): Path

    data class Local(
        override val name: String,
        private val localPath: Path,
    ) : TomlDependency() {
        override fun localPath(): Path = localPath
    }

    data class Git(
        override val name: String,
        private val repo: String,
        private val rev: String,
        private val subdir: String,
    ) : TomlDependency() {

        override fun localPath(): Path {
            val home = SystemProperties.getUserHome()
            // Support legacy cache naming and current Sui naming.
            val dirNameLegacy = dirNameLegacy(repo, rev)
            val legacyPath = Paths.get(home, ".move", dirNameLegacy, subdir)
            if (Files.exists(legacyPath)) {
                return legacyPath
            } else {
                val dirNameSui = dirNameSui(repo, rev)
                val suiPath = Paths.get(home, ".move", dirNameSui, subdir)
                return suiPath
            }
        }

        companion object {
            fun dirNameLegacy(repo: String, rev: String): String {
                val sanitizedRepoName = repo.replace(Regex("[/:.@]"), "_")
                val legacyRevName = rev.replace("/", "_")
                return "${sanitizedRepoName}_$legacyRevName"
            }

            fun dirNameSui(repo: String, rev: String): String {
                val sanitizedRepoName = repo.replace(Regex("[/:.@]"), "_")
                val suiRevName = rev.replace("/", "__")
                return "${sanitizedRepoName}_$suiRevName"
            }
        }
    }
}
