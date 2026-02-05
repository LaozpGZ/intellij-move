package org.sui.cli

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sui.cli.manifest.AptosConfigYaml
import org.sui.cli.manifest.MoveToml
import org.sui.cli.manifest.SuiConfigYaml
import org.sui.lang.core.psi.MvElement
import org.sui.lang.moveProject
import org.sui.lang.toNioPathOrNull
import org.sui.openapiext.common.isLightTestFile
import org.sui.openapiext.common.isUnitTestFile
import org.sui.openapiext.common.isUnitTestMode
import org.sui.openapiext.pathAsPath
import org.sui.openapiext.resolveExisting
import org.sui.openapiext.toPsiFile
import org.toml.lang.psi.TomlFile
import java.nio.file.Path
import kotlin.io.path.relativeToOrNull

enum class MoveEdition(
    val displayName: String,
    val tomlValues: Set<String>
) {
    MOVE_1("Move 1", setOf("1", "move-1")),
    MOVE_2024_ALPHA("Move 2024 Alpha", setOf("2024.alpha", "2024-alpha", "move-2024-alpha")),
    MOVE_2024("Move 2024", setOf("2024", "move-2024"));

    val isMove2024: Boolean get() = this != MOVE_1

    companion object {
        val DEFAULT: MoveEdition = MOVE_1
        private val lookup: Map<String, MoveEdition> =
            values().flatMap { edition -> edition.tomlValues.map { it to edition } }.toMap()

        val supportedEditionValues: List<String> = listOf("1", "2024", "2024.alpha")

        fun fromToml(rawValue: String?): MoveEdition? {
            if (rawValue.isNullOrBlank()) return null
            val normalized = rawValue.trim().lowercase()
            return lookup[normalized]
        }
    }
}

data class MoveLanguageFeatures(
    val receiverStyleFunctions: Boolean,
    val resourceAccessControl: Boolean,
    val indexExpr: Boolean,
    val publicPackageVisibility: Boolean,
    val macroFunctions: Boolean,
    val typeKeyword: Boolean,
    val publicStructRequired: Boolean,
    val letMutRequired: Boolean,
    val publicFriendDisabled: Boolean,
) {
    companion object {
        val DEFAULT: MoveLanguageFeatures = fromEdition(MoveEdition.DEFAULT)

        fun fromEdition(edition: MoveEdition): MoveLanguageFeatures {
            val move2024 = edition.isMove2024
            return MoveLanguageFeatures(
                receiverStyleFunctions = move2024,
                resourceAccessControl = false,
                indexExpr = move2024,
                publicPackageVisibility = true,
                macroFunctions = move2024,
                typeKeyword = move2024,
                publicStructRequired = move2024,
                letMutRequired = move2024,
                publicFriendDisabled = move2024,
            )
        }
    }
}

data class MovePackage(
    val project: Project,
    val contentRoot: VirtualFile,
    val packageName: String,
    val tomlMainAddresses: PackageAddresses,
    val edition: MoveEdition = MoveEdition.DEFAULT,
) {
    val manifestFile: VirtualFile get() = contentRoot.findChild(MvConstants.MANIFEST_FILE)!!

    val manifestTomlFile: TomlFile get() = manifestFile.toPsiFile(project) as TomlFile
    val moveToml: MoveToml get() = MoveToml.fromTomlFile(this.manifestTomlFile)
    val languageFeatures: MoveLanguageFeatures get() = MoveLanguageFeatures.fromEdition(edition)

//    val packageName = this.moveToml.packageName ?: ""

    val sourcesFolder: VirtualFile? get() = contentRoot.takeIf { it.isValid }?.findChild("sources")
    val testsFolder: VirtualFile? get() = contentRoot.takeIf { it.isValid }?.findChild("tests")
    val scriptsFolder: VirtualFile? get() = contentRoot.takeIf { it.isValid }?.findChild("scripts")

    val aptosConfigYaml: AptosConfigYaml?
        get() {
            var root: VirtualFile? = contentRoot
            while (true) {
                if (root == null) break
                val candidatePath = root
                    .findChild(".aptos")
                    ?.takeIf { it.isDirectory }
                    ?.findChild("config.yaml")
                if (candidatePath != null) {
                    return AptosConfigYaml.fromPath(candidatePath.pathAsPath)
                }
                root = root.parent
            }
            return null
        }
    val suiConfigYaml: SuiConfigYaml?
        get() {
            return null
        }


    fun moveFolders(): List<VirtualFile> = listOfNotNull(sourcesFolder, testsFolder, scriptsFolder)

    fun layoutPaths(): List<Path> {
        val rootPath = contentRoot.takeIf { it.isValid }?.toNioPathOrNull() ?: return emptyList()
        val names = listOf(
            *MvProjectLayout.sourcesDirs,
            MvProjectLayout.testsDir,
            MvProjectLayout.buildDir
        )
        return names.mapNotNull { rootPath.resolveExisting(it) }
    }

    fun addresses(): PackageAddresses {
//        val tomlMainAddresses = tomlMainAddresses
//        val tomlDevAddresses = moveToml.declaredAddresses()

        val addresses = mutableAddressMap()
        addresses.putAll(tomlMainAddresses.values)
        // add placeholders defined in this package as address values
        addresses.putAll(tomlMainAddresses.placeholdersAsValues())
        // devs on top
//        addresses.putAll(tomlDevAddresses.values)

        return PackageAddresses(addresses, tomlMainAddresses.placeholders)
    }

    override fun hashCode(): Int = this.contentRoot.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is MovePackage) return false
        if (this === other) return true
        return this.contentRoot == other.contentRoot
    }

    companion object {
        fun fromMoveToml(moveToml: MoveToml): MovePackage {
            val contentRoot = moveToml.tomlFile.virtualFile.parent
            return MovePackage(
                moveToml.project, contentRoot,
                packageName = moveToml.packageName ?: "",
                tomlMainAddresses = moveToml.declaredAddresses(),
                edition = moveToml.edition,
            )
        }
    }
}

val MvElement.containingMovePackage: MovePackage?
    get() {
        val elementFile = this.containingFile.virtualFile
        if (elementFile.isUnitTestFile) {
            // temp file for light unit tests
            return project.testMoveProject.currentPackage
        }
        val elementPath = this.containingFile?.toNioPathOrNull() ?: return null
        val allPackages = this.moveProject?.movePackages().orEmpty()
        return allPackages.find {
            val folderPaths = it.moveFolders().mapNotNull { it.toNioPathOrNull() }
            for (folderPath in folderPaths) {
                if (elementPath.relativeToOrNull(folderPath) != null) {
                    return it
                }
            }
            false
        }
    }
