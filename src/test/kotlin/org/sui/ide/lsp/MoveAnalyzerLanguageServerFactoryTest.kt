package org.sui.ide.lsp

import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvTestBase

class MoveAnalyzerLanguageServerFactoryTest : MvTestBase() {
    fun `test language server enablement follows project settings`() {
        val factory = MoveAnalyzerLanguageServerFactory()
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerEnabled = true
        }

        check(factory.isEnabled(project))

        factory.setEnabled(false, project)
        check(!factory.isEnabled(project))

        factory.setEnabled(true, project)
        check(factory.isEnabled(project))
    }
}

