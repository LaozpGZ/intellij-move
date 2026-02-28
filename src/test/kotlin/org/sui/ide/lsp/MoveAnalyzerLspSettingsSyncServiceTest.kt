package org.sui.ide.lsp

import junit.framework.TestCase
import org.sui.cli.settings.MvProjectSettingsService

class MoveAnalyzerLspSettingsSyncServiceTest : TestCase() {
    fun `test detects move analyzer enabled change`() {
        val oldState = MvProjectSettingsService.MoveProjectSettings().apply {
            moveAnalyzerEnabled = true
        }
        val newState = oldState.copy().apply {
            moveAnalyzerEnabled = false
        }
        val event = MvProjectSettingsService.SettingsChangedEvent(oldState, newState)

        check(MoveAnalyzerLspSettingsSyncService.isMoveAnalyzerSettingsChanged(event)) {
            "Expected enabled toggle to require LSP sync"
        }
    }

    fun `test detects move analyzer path change`() {
        val oldState = MvProjectSettingsService.MoveProjectSettings().apply {
            moveAnalyzerPath = "/tmp/old-move-analyzer"
        }
        val newState = oldState.copy().apply {
            moveAnalyzerPath = "/tmp/new-move-analyzer"
        }
        val event = MvProjectSettingsService.SettingsChangedEvent(oldState, newState)

        check(MoveAnalyzerLspSettingsSyncService.isMoveAnalyzerSettingsChanged(event)) {
            "Expected path change to require LSP sync"
        }
    }

    fun `test ignores unrelated settings change`() {
        val oldState = MvProjectSettingsService.MoveProjectSettings().apply {
            moveAnalyzerEnabled = true
            moveAnalyzerPath = "/tmp/move-analyzer"
            skipFetchLatestGitDeps = true
        }
        val newState = oldState.copy().apply {
            skipFetchLatestGitDeps = false
        }
        val event = MvProjectSettingsService.SettingsChangedEvent(oldState, newState)

        check(!MoveAnalyzerLspSettingsSyncService.isMoveAnalyzerSettingsChanged(event)) {
            "Expected unrelated settings updates to skip LSP sync"
        }
    }
}
