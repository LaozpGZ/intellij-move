package org.sui.ide.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.sui.cli.settings.MvProjectSettingsService
import org.sui.cli.settings.MvProjectSettingsService.MoveProjectSettings
import org.sui.cli.settings.MvProjectSettingsServiceBase
import org.sui.cli.settings.MvProjectSettingsServiceBase.Companion.MOVE_SETTINGS_TOPIC
import org.sui.cli.settings.MvProjectSettingsServiceBase.MoveSettingsListener
import org.sui.cli.settings.MvProjectSettingsServiceBase.SettingsChangedEventBase
import org.sui.openapiext.common.isUnitTestMode

class MoveAnalyzerLspSettingsSyncService(private val project: Project) : MoveSettingsListener, Disposable {
    init {
        project.messageBus.connect(this).subscribe(MOVE_SETTINGS_TOPIC, this)
    }

    override fun <T : MvProjectSettingsServiceBase.MvProjectSettingsBase<T>> settingsChanged(e: SettingsChangedEventBase<T>) {
        if (isUnitTestMode) return
        val event = e as? MvProjectSettingsService.SettingsChangedEvent ?: return
        if (!isMoveAnalyzerSettingsChanged(event)) return

        val wrappers = MoveAnalyzerLspServerController.getMoveAnalyzerWrappers(project)
        if (wrappers.isEmpty()) {
            LOG.info("move-analyzer settings changed, no running LSP wrappers to sync")
            return
        }

        if (event.newState.moveAnalyzerEnabled) {
            wrappers.forEach { wrapper ->
                runCatching { wrapper.restart() }
                    .onFailure { error ->
                        LOG.warn("Failed to restart move-analyzer LSP wrapper", error)
                    }
            }
            LOG.info("Restarted ${wrappers.size} move-analyzer LSP wrapper(s) after settings change")
        } else {
            wrappers.forEach { wrapper ->
                runCatching { wrapper.stopAndDisable() }
                    .onFailure { error ->
                        LOG.warn("Failed to stop move-analyzer LSP wrapper", error)
                    }
            }
            LOG.info("Stopped ${wrappers.size} move-analyzer LSP wrapper(s) because LSP was disabled")
        }
    }

    override fun dispose() {}

    companion object {
        private val LOG = logger<MoveAnalyzerLspSettingsSyncService>()

        internal fun isMoveAnalyzerSettingsChanged(event: MvProjectSettingsService.SettingsChangedEvent): Boolean {
            return event.isChanged(MoveProjectSettings::moveAnalyzerEnabled)
                || event.isChanged(MoveProjectSettings::moveAnalyzerPath)
        }
    }
}
