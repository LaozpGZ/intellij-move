package org.sui.ide.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.sui.cli.settings.moveSettings

class MoveAnalyzerLanguageServerFactory : LanguageServerFactory, LanguageServerEnablementSupport {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return MoveAnalyzerStreamConnectionProvider(project)
    }

    override fun isEnabled(project: Project): Boolean = project.moveSettings.moveAnalyzerEnabled

    override fun setEnabled(enabled: Boolean, project: Project) {
        if (project.moveSettings.moveAnalyzerEnabled == enabled) return
        project.moveSettings.modify { it.moveAnalyzerEnabled = enabled }
    }
}

private class MoveAnalyzerStreamConnectionProvider(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        setCommandLine(MoveAnalyzerCommandProvider.createCommandLine(project))
    }
}
