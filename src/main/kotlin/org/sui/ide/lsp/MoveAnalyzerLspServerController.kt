package org.sui.ide.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor

object MoveAnalyzerLspServerController {
    private const val MOVE_ANALYZER_SERVER_ID: String = "suiMoveAnalyzer"

    fun getMoveAnalyzerWrappers(project: Project): List<LanguageServerWrapper> {
        return LanguageServiceAccessor.getInstance(project)
            .getStartedServers()
            .asSequence()
            .filter { wrapper ->
                wrapper.project == project && wrapper.serverDefinition.id == MOVE_ANALYZER_SERVER_ID
            }
            .toList()
    }
}
