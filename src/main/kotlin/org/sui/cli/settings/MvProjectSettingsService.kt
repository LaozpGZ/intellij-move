package org.sui.cli.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.sui.bytecode.createDisposableOnFileChange
import org.sui.cli.MoveLanguageFeatures
import org.sui.cli.MoveProject
import org.sui.cli.MoveProjectsService
import org.sui.cli.runConfigurations.aptos.Aptos
import org.sui.cli.runConfigurations.sui.Sui
import org.sui.cli.settings.MvProjectSettingsService.MoveProjectSettings
import org.sui.cli.settings.aptos.AptosExecType
import org.sui.cli.settings.sui.SuiExecType
import org.sui.openapiext.common.isUnitTestMode
import org.sui.stdext.exists
import org.sui.stdext.isExecutableFile
import java.nio.file.Path

private const val SERVICE_NAME: String = "SuiMoveProjectSettingsService_1"

@State(
    name = SERVICE_NAME,
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class MvProjectSettingsService(
    project: Project
) :
    MvProjectSettingsServiceBase<MoveProjectSettings>(project, MoveProjectSettings()) {

    val aptosExecType: AptosExecType get() = state.aptosExecType
    val localAptosPath: String? get() = state.localAptosPath
    val fetchAptosDeps: Boolean get() = state.fetchAptosDeps

    // sui
    val suiExecType: SuiExecType get() = state.suiExecType
    val localSuiPath: String? get() = state.localSuiPath
    val fetchSuiDeps: Boolean get() = state.fetchSuiDeps

    val disableTelemetry: Boolean get() = state.disableTelemetry
    val skipFetchLatestGitDeps: Boolean get() = state.skipFetchLatestGitDeps
    val dumpStateOnTestFailure: Boolean get() = state.dumpStateOnTestFailure

    val enableReceiverStyleFunctions: Boolean get() = effectiveLanguageFeatures().receiverStyleFunctions
    val enableResourceAccessControl: Boolean get() = effectiveLanguageFeatures().resourceAccessControl
    val enableIndexExpr: Boolean get() = effectiveLanguageFeatures().indexExpr
    val enablePublicPackage: Boolean get() = effectiveLanguageFeatures().publicPackageVisibility
    val enableMacroFunctions: Boolean get() = effectiveLanguageFeatures().macroFunctions
    val enableTypeKeyword: Boolean get() = effectiveLanguageFeatures().typeKeyword
    val requirePublicStruct: Boolean get() = effectiveLanguageFeatures().publicStructRequired
    val requireLetMut: Boolean get() = effectiveLanguageFeatures().letMutRequired
    val publicFriendDisabled: Boolean get() = effectiveLanguageFeatures().publicFriendDisabled
    val addCompilerV2CLIFlags: Boolean get() = state.addCompilerV2CLIFlags

    fun effectiveLanguageFeatures(moveProject: MoveProject? = null): MoveLanguageFeatures {
        val baseFeatures =
            moveProject?.languageFeatures
                ?: findRelevantMoveProject()?.languageFeatures
                ?: MoveLanguageFeatures.DEFAULT
        return applyFeatureOverrides(baseFeatures)
    }

    // default values for settings
    class MoveProjectSettings : MvProjectSettingsBase<MoveProjectSettings>() {
        @AffectsMoveProjectsMetadata
        var aptosExecType: AptosExecType by enum(defaultAptosExecType)

        @AffectsMoveProjectsMetadata
        var localAptosPath: String? by string()

        @AffectsMoveProjectsMetadata
        var suiExecType: SuiExecType by enum(defaultSuiExecType)

        @AffectsMoveProjectsMetadata
        var localSuiPath: String? by string()

        @AffectsHighlighting
        var enableReceiverStyleFunctions: Boolean by property(true)

        @AffectsParseTree
        var enableResourceAccessControl: Boolean by property(false)

        @AffectsHighlighting
        var enableIndexExpr: Boolean by property(true)

        @AffectsHighlighting
        var enablePublicPackage: Boolean by property(true)

        @AffectsParseTree
        var enableMacroFunctions: Boolean by property(true)

        @AffectsHighlighting
        var enableTypeKeyword: Boolean by property(true)

        @AffectsHighlighting
        var requirePublicStruct: Boolean by property(true)

        @AffectsHighlighting
        var requireLetMut: Boolean by property(true)

        var featureOverridesActive: Boolean by property(false)

        @AffectsMoveProjectsMetadata
        var fetchAptosDeps: Boolean by property(false)

        @AffectsMoveProjectsMetadata
        var fetchSuiDeps: Boolean by property(false)

        var disableTelemetry: Boolean by property(true)

        // change to true here to not annoy the users with constant updates
        var skipFetchLatestGitDeps: Boolean by property(true)
        var dumpStateOnTestFailure: Boolean by property(false)

        var addCompilerV2CLIFlags: Boolean by property(false)

        override fun copy(): MoveProjectSettings {
            val state = MoveProjectSettings()
            state.copyFrom(this)
            return state
        }
    }

    override fun createSettingsChangedEvent(
        oldEvent: MoveProjectSettings,
        newEvent: MoveProjectSettings
    ): SettingsChangedEvent {
        if (!newEvent.featureOverridesActive && oldEvent.featureOverridesActive == false) {
            if (oldEvent.enableReceiverStyleFunctions != newEvent.enableReceiverStyleFunctions
                || oldEvent.enableIndexExpr != newEvent.enableIndexExpr
                || oldEvent.enablePublicPackage != newEvent.enablePublicPackage
                || oldEvent.enableResourceAccessControl != newEvent.enableResourceAccessControl
                || oldEvent.enableMacroFunctions != newEvent.enableMacroFunctions
                || oldEvent.enableTypeKeyword != newEvent.enableTypeKeyword
                || oldEvent.requirePublicStruct != newEvent.requirePublicStruct
                || oldEvent.requireLetMut != newEvent.requireLetMut
            ) {
                newEvent.featureOverridesActive = true
            }
        }
        return SettingsChangedEvent(oldEvent, newEvent)
    }

    class SettingsChangedEvent(
        oldState: MoveProjectSettings,
        newState: MoveProjectSettings
    ) : SettingsChangedEventBase<MoveProjectSettings>(oldState, newState)

    companion object {
        private val defaultAptosExecType
            get() =
                if (AptosExecType.isPreCompiledSupportedForThePlatform) AptosExecType.BUNDLED else AptosExecType.LOCAL
        private val defaultSuiExecType
            get() = SuiExecType.LOCAL
    }

    private fun applyFeatureOverrides(base: MoveLanguageFeatures): MoveLanguageFeatures {
        if (!featureOverridesActive()) return base
        val publicPackageVisibility = state.enablePublicPackage
        return base.copy(
            receiverStyleFunctions = state.enableReceiverStyleFunctions,
            resourceAccessControl = state.enableResourceAccessControl,
            indexExpr = state.enableIndexExpr,
            publicPackageVisibility = publicPackageVisibility,
            macroFunctions = state.enableMacroFunctions,
            typeKeyword = state.enableTypeKeyword,
            publicStructRequired = state.requirePublicStruct,
            letMutRequired = state.requireLetMut,
            publicFriendDisabled = base.publicFriendDisabled || publicPackageVisibility,
        )
    }

    private fun featureOverridesActive(): Boolean {
        return state.featureOverridesActive || state.hasNonDefaultFeatureOverrides()
    }

    private fun MoveProjectSettings.hasNonDefaultFeatureOverrides(): Boolean {
        return !isDefaultValue(MoveProjectSettings::enableReceiverStyleFunctions)
            || !isDefaultValue(MoveProjectSettings::enableIndexExpr)
            || !isDefaultValue(MoveProjectSettings::enablePublicPackage)
            || !isDefaultValue(MoveProjectSettings::enableResourceAccessControl)
            || !isDefaultValue(MoveProjectSettings::enableMacroFunctions)
            || !isDefaultValue(MoveProjectSettings::enableTypeKeyword)
            || !isDefaultValue(MoveProjectSettings::requirePublicStruct)
            || !isDefaultValue(MoveProjectSettings::requireLetMut)
    }

    private fun MoveProjectSettings.isDefaultValue(prop: kotlin.reflect.KProperty1<MoveProjectSettings, *>): Boolean {
        val defaultState = MoveProjectSettings()
        return prop.get(this) == prop.get(defaultState)
    }

    private fun findRelevantMoveProject(): MoveProject? {
        val moveProjectsService = project.getService(MoveProjectsService::class.java) ?: return null
        val selectedFile =
            if (!isUnitTestMode && ApplicationManager.getApplication().isDispatchThread) {
                FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile
            } else {
                null
            }
        if (selectedFile != null) {
            val moveProject = moveProjectsService.findMoveProjectForFile(selectedFile)
            if (moveProject != null) return moveProject
        }
        return moveProjectsService.allProjects.firstOrNull()
    }
}

val Project.moveSettings: MvProjectSettingsService get() = service()

val Project.moveLanguageFeatures: MoveLanguageFeatures
    get() = this.moveSettings.effectiveLanguageFeatures()

fun Project.getAptosCli(parentDisposable: Disposable? = null): Aptos? {
    val aptosExecPath =
        AptosExecType.aptosExecPath(
            this.moveSettings.aptosExecType,
            this.moveSettings.localAptosPath
        )
    val aptos = aptosExecPath?.let { Aptos(it, parentDisposable) }
    return aptos
}

fun Project.getSuiCli(parentDisposable: Disposable? = null): Sui? {
    val suiExecPath =
        SuiExecType.suiExecPath(
            this.moveSettings.suiExecType,
            this.moveSettings.localSuiPath
        )
    val sui = suiExecPath?.let { Sui(it, parentDisposable) }
    return sui
}

val Project.isAptosConfigured: Boolean get() = this.getAptosCli() != null

val Project.isSuiConfigured: Boolean get() = this.getSuiCli() != null

fun Project.getAptosCliDisposedOnFileChange(file: VirtualFile): Aptos? {
    val anyChangeDisposable = this.createDisposableOnFileChange(file)
    return this.getAptosCli(anyChangeDisposable)
}

fun Project.getSuiCliDisposedOnFileChange(file: VirtualFile): Sui? {
    val anyChangeDisposable = this.createDisposableOnFileChange(file)
    return this.getSuiCli(anyChangeDisposable)
}

val Project.aptosExecPath: Path? get() = this.getAptosCli()?.cliLocation
val Project.suiExecPath: Path? get() = this.getSuiCli()?.cliLocation

fun Path?.isValidExecutable(): Boolean {
    return this != null
            && this.toString().isNotBlank()
            && this.exists()
            && this.isExecutableFile()
}

fun isDebugModeEnabled(): Boolean = Registry.`is`("org.sui.debug.enabled")
fun isTypeUnknownAsError(): Boolean = Registry.`is`("org.sui.types.highlight.unknown.as.error")

fun debugError(message: String) {
    if (isDebugModeEnabled()) {
        error(message)
    }
}

fun <T> debugErrorOrFallback(message: String, fallback: T): T {
    if (isDebugModeEnabled()) {
        error(message)
    }
    return fallback
}

fun <T> debugErrorOrFallback(message: String, cause: Throwable?, fallback: () -> T): T {
    if (isDebugModeEnabled()) {
        throw IllegalStateException(message, cause)
    }
    return fallback()
}
