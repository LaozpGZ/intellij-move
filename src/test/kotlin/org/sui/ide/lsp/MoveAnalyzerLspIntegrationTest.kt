package org.sui.ide.lsp

import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionParams
import com.redhat.devtools.lsp4ij.usages.LocationData
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.util.concurrent.TimeUnit

class MoveAnalyzerLspIntegrationTest : MvProjectTestBase() {
    fun `test diagnostics are reported from move analyzer`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun call() {
                            let _ = broken/*caret*/;
                        }
                    }
                    """
                )
            }
        }

        val diagnostic = waitForLspDiagnostic("fake lsp diagnostic")
        val expectedStart = myFixture.file.text.indexOf("broken")
        check(expectedStart >= 0) { "Failed to locate `broken` in source" }
        val expectedPosition = LSPIJUtils.toPosition(expectedStart, myFixture.editor.document)
        check(diagnostic.range.start.line == expectedPosition.line) {
            "Unexpected diagnostic line: ${diagnostic.range.start.line}, expected: ${expectedPosition.line}"
        }
        check(diagnostic.range.start.character == expectedPosition.character) {
            "Unexpected diagnostic character: ${diagnostic.range.start.character}, expected: ${expectedPosition.character}"
        }
    }

    fun `test goto definition uses move analyzer result`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}
                        
                        fun call() {
                            tar/*caret*/get();
                        }
                    }
                    """
                )
            }
        }

        val uri = LSPIJUtils.toUriAsString(myFixture.file)
        val offset = myFixture.caretOffset
        val position = LSPIJUtils.toPosition(offset, myFixture.editor.document)
        val params = LSPDefinitionParams(TextDocumentIdentifier(uri), position, offset)

        val definitions = waitForDefinitions(params)
        check(definitions.size == 1) {
            "Expected exactly one definition result, got ${definitions.size}: $definitions"
        }

        val location = definitions.single().location()
        check(location.uri == uri) {
            "Expected definition uri `$uri`, got `${location.uri}`"
        }

        val targetOffset = myFixture.file.text.indexOf("target")
        check(targetOffset >= 0) { "Failed to locate declaration `target` in source" }
        val expectedPosition = LSPIJUtils.toPosition(targetOffset, myFixture.editor.document)

        check(location.range.start.line == expectedPosition.line) {
            "Unexpected definition line: ${location.range.start.line}, expected: ${expectedPosition.line}"
        }
        check(location.range.start.character == expectedPosition.character) {
            "Unexpected definition character: ${location.range.start.character}, expected: ${expectedPosition.character}"
        }
    }

    private fun configureMoveAnalyzer() {
        val executable = FakeMoveAnalyzerServer.createExecutable()
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerEnabled = true
            it.moveAnalyzerPath = executable.toString()
        }
    }

    private fun waitForLspDiagnostic(description: String): Diagnostic {
        val fileUri = LSPIJUtils.toUri(myFixture.file)
        triggerLspRequest()

        var found: Diagnostic? = null
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP diagnostic `$description`",
            retries = 1500
        ) {
            LanguageServiceAccessor.getInstance(project).processLanguageServers(myFixture.file) { wrapper ->
                val openedDocument = wrapper.getOpenedDocument(fileUri) ?: return@processLanguageServers
                found = openedDocument.diagnostics.firstOrNull { it.message == description } ?: found
            }
            found != null
        }
        return found ?: error("LSP diagnostic `$description` was not produced")
    }

    private fun triggerLspRequest() {
        val uri = LSPIJUtils.toUriAsString(myFixture.file)
        val offset = myFixture.caretOffset
        val position = LSPIJUtils.toPosition(offset, myFixture.editor.document)
        val params = LSPDefinitionParams(TextDocumentIdentifier(uri), position, offset)

        try {
            LSPFileSupport.getSupport(myFixture.file).definitionSupport
                .getDefinitions(params)
                .get(1, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // The request is only used to force LSP session initialization.
        }
    }

    private fun waitForDefinitions(params: LSPDefinitionParams): List<LocationData> {
        var result: List<LocationData> = emptyList()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP definition result",
            retries = 300
        ) {
            val future = LSPFileSupport.getSupport(myFixture.file).definitionSupport.getDefinitions(params)
            result = try {
                future.get(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
                emptyList()
            }
            result.isNotEmpty()
        }
        return result
    }
}
