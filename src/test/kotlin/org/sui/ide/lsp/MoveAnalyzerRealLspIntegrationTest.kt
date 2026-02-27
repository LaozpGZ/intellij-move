package org.sui.ide.lsp

import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionParams
import com.redhat.devtools.lsp4ij.usages.LocationData
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.util.concurrent.TimeUnit

class MoveAnalyzerRealLspIntegrationTest : MvProjectTestBase() {
    override fun tearDown() {
        try {
            stopLanguageServers()
        } finally {
            super.tearDown()
        }
    }

    fun `test diagnostics are reported from real move analyzer`() {
        if (!configureRealMoveAnalyzer()) return
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun call() {
                            let _ = missing_symbol/*caret*/;
                        }
                    }
                    """
                )
            }
        }

        val diagnostic = waitForDiagnosticCovering("missing_symbol")
        check(diagnostic.message.isNotBlank()) {
            "Expected diagnostic message from real move-analyzer, got blank message"
        }
    }

    fun `test goto definition request is handled by real move analyzer`() {
        if (!configureRealMoveAnalyzer()) return
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}

                        fun call() {
                            tar/*caret*/get();
                            let _ = missing_symbol;
                        }
                    }
                    """
                )
            }
        }

        // Real analyzer may need one full symbolication pass; waiting for diagnostics ensures
        // the current file has been analyzed before definition assertions.
        waitForDiagnosticCovering("missing_symbol")

        val uri = LSPIJUtils.toUriAsString(myFixture.file)
        val offset = myFixture.caretOffset
        val position = LSPIJUtils.toPosition(offset, myFixture.editor.document)
        val params = LSPDefinitionParams(TextDocumentIdentifier(uri), position, offset)

        val definitions = waitForDefinitionResponse(params)
        check(definitions.all { locationData ->
            locationData.location().uri.isNotBlank()
        }) {
            "Expected non-empty definition URIs when definition results are present, got $definitions"
        }
    }

    private fun configureRealMoveAnalyzer(): Boolean {
        if (!isRealAnalyzerTestsEnabled()) return false

        val configuredPath = System.getProperty(REAL_ANALYZER_PROPERTY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: System.getenv(REAL_ANALYZER_ENV)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        val executable = MoveAnalyzerPathResolver.resolveExecutable(project, configuredPath)
        if (executable == null) return false

        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerEnabled = true
            it.moveAnalyzerPath = executable.toString()
        }
        return true
    }

    private data class CaretContext(val uri: String, val offset: Int, val position: Position)

    private fun currentCaretContext(): CaretContext {
        val uri = LSPIJUtils.toUriAsString(myFixture.file)
            ?: error("Failed to build file URI for `${myFixture.file.name}`")
        val offset = myFixture.caretOffset
        val position = LSPIJUtils.toPosition(offset, myFixture.editor.document)
        return CaretContext(uri, offset, position)
    }

    private fun waitForDiagnosticCovering(symbol: String): Diagnostic {
        val targetOffset = myFixture.file.text.indexOf(symbol)
        check(targetOffset >= 0) { "Failed to locate `$symbol` in source" }
        val targetPosition = LSPIJUtils.toPosition(targetOffset, myFixture.editor.document)

        val fileUri = LSPIJUtils.toUri(myFixture.file)
        var found: Diagnostic? = null
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for real move-analyzer diagnostic covering `$symbol`",
            retries = 6000
        ) {
            triggerLspRequest()
            LanguageServiceAccessor.getInstance(project).processLanguageServers(myFixture.file) { wrapper ->
                val openedDocument = wrapper.getOpenedDocument(fileUri) ?: return@processLanguageServers
                found = openedDocument.diagnostics.firstOrNull { rangeContains(it.range, targetPosition) } ?: found
            }
            found != null
        }
        return found ?: error("Real move-analyzer diagnostic for `$symbol` was not produced")
    }

    private fun triggerLspRequest() {
        val context = currentCaretContext()
        val params = LSPDefinitionParams(TextDocumentIdentifier(context.uri), context.position, context.offset)

        try {
            LSPFileSupport.getSupport(myFixture.file).definitionSupport
                .getDefinitions(params)
                .get(1, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // This request is only used to force LSP session initialization.
        }
    }

    private fun waitForDefinitionResponse(params: LSPDefinitionParams): List<LocationData> {
        var result: List<LocationData>? = null
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for real move-analyzer definition response",
            retries = 1200
        ) {
            val future = LSPFileSupport.getSupport(myFixture.file).definitionSupport.getDefinitions(params)
            result = try {
                future.get(250, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                null
            }
            if (result == null) {
                triggerLspRequest()
            }
            result != null
        }
        return result ?: error("Real move-analyzer did not return a definition response")
    }

    private fun rangeContains(range: Range, position: Position): Boolean {
        return comparePosition(position, range.start) >= 0 && comparePosition(position, range.end) <= 0
    }

    private fun comparePosition(a: Position, b: Position): Int {
        if (a.line != b.line) return a.line.compareTo(b.line)
        return a.character.compareTo(b.character)
    }

    private fun stopLanguageServers() {
        val accessor = LanguageServiceAccessor.getInstance(project)
        val wrappers = accessor.getStartedServers().toList()
        wrappers.forEach { wrapper ->
            try {
                wrapper.dispose(true)
            } catch (_: Throwable) {
                wrapper.stopAndDisable()
                wrapper.dispose()
            }
        }
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for real LSP servers shutdown",
            retries = 500
        ) {
            wrappers.all { it.isDisposed }
        }
    }

    private companion object {
        const val REAL_ANALYZER_TESTS_PROPERTY: String = "sui.moveAnalyzer.real.tests"
        const val REAL_ANALYZER_TESTS_ENV: String = "SUI_MOVE_ANALYZER_REAL_TESTS"
        const val REAL_ANALYZER_PROPERTY: String = "sui.moveAnalyzer.real.path"
        const val REAL_ANALYZER_ENV: String = "SUI_MOVE_ANALYZER_REAL_PATH"
    }

    private fun isRealAnalyzerTestsEnabled(): Boolean {
        fun parseBooleanFlag(raw: String?): Boolean? {
            return when (raw?.trim()?.lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                null, "" -> null
                else -> null
            }
        }

        return parseBooleanFlag(System.getProperty(REAL_ANALYZER_TESTS_PROPERTY))
            ?: parseBooleanFlag(System.getenv(REAL_ANALYZER_TESTS_ENV))
            ?: false
    }
}
