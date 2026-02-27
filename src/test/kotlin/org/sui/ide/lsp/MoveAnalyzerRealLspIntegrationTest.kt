package org.sui.ide.lsp

import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionParams
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceParams
import com.redhat.devtools.lsp4ij.features.rename.WorkspaceEditData
import com.redhat.devtools.lsp4ij.usages.LocationData
import com.intellij.testFramework.common.ThreadLeakTracker
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class MoveAnalyzerRealLspIntegrationTest : MvProjectTestBase() {
    override fun tearDown() {
        try {
            if (!project.isDisposed) {
                stopLanguageServers()
            }
        } catch (_: Throwable) {
            // Avoid masking test failures with teardown disposal races.
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

    fun `test references request is handled by real move analyzer`() {
        if (!configureRealMoveAnalyzer()) return
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}

                        fun call() {
                            target();
                            tar/*caret*/get();
                            let _ = missing_symbol;
                        }
                    }
                    """
                )
            }
        }

        waitForDiagnosticCovering("missing_symbol")
        val context = currentCaretContext()
        val params = LSPReferenceParams(
            TextDocumentIdentifier(context.uri),
            context.position,
            context.offset
        )
        val references = waitForReferencesResponse(params)
        check(references.all { locationData ->
            locationData.location().uri.isNotBlank()
        }) {
            "Expected non-empty reference URIs when reference results are present, got $references"
        }
    }

    fun `test rename request is handled by real move analyzer`() {
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
                            target();
                            let _ = missing_symbol;
                        }
                    }
                    """
                )
            }
        }

        waitForDiagnosticCovering("missing_symbol")
        val newName = "renamed_target"
        val edits = waitForRenameEdits(newName)
        check(edits.all { workspaceEdit ->
            workspaceEdit.changes?.keys?.all { it.isNotBlank() } ?: true
        }) {
            "Expected non-empty URIs in rename workspace edits when present, got $edits"
        }

        val textEdits = edits.flatMap { workspaceEdit ->
            workspaceEdit.changes?.values?.flatten().orEmpty()
        }
        check(textEdits.all { it.newText == newName }) {
            "Expected rename edits to use `$newName` when edits are present, got $textEdits"
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
        registerLongRunningAnalyzerThreads(executable.toString())

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

    private fun waitForReferencesResponse(params: LSPReferenceParams): List<LocationData> {
        var result: List<LocationData>? = null
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for real move-analyzer references response",
            retries = 1200
        ) {
            val future = LSPFileSupport.getSupport(myFixture.file).referenceSupport.getReferences(params)
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
        return result ?: error("Real move-analyzer did not return a references response")
    }

    private fun waitForRenameEdits(newName: String): List<WorkspaceEdit> {
        var edits: List<WorkspaceEdit> = emptyList()
        var requestTriggered = false
        triggerLspRequest()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for real move-analyzer rename response",
            retries = 1200
        ) {
            val renameParams = createRenameParams(newName)
            if (renameParams == null) {
                triggerLspRequest()
                return@runWithInvocationEventsDispatching false
            }
            requestTriggered = true
            val renameDataList = try {
                requestFeatureList(
                    support = LSPFileSupport.getSupport(myFixture.file).renameSupport,
                    methodName = "getRename",
                    params = renameParams,
                    timeoutMillis = 250
                )
            } catch (_: Exception) {
                emptyList<Any>()
            }
            edits = extractWorkspaceEdits(renameDataList)
            true
        }
        check(requestTriggered) {
            "Real move-analyzer rename request chain was not triggered"
        }
        return edits
    }

    private fun createRenameParams(newName: String): Any? {
        val context = currentCaretContext()
        val languageServers = getLanguageServersForCurrentFile()
        if (languageServers.isEmpty()) return null

        val paramsClass = Class.forName("com.redhat.devtools.lsp4ij.features.rename.LSPRenameParams")
        val constructor = paramsClass.declaredConstructors.firstOrNull { it.parameterCount == 3 }
            ?: return null
        constructor.trySetAccessible()
        val params = constructor.newInstance(
            TextDocumentIdentifier(context.uri),
            context.position,
            languageServers
        ) ?: return null

        val setNewName = paramsClass.methods.firstOrNull {
            it.name == "setNewName" && it.parameterCount == 1
        } ?: return null
        setNewName.invoke(params, newName)
        return params
    }

    private fun getLanguageServersForCurrentFile(): List<*> {
        val allowAll = Predicate<LSPClientFeatures> { true }
        return try {
            val accessor = LanguageServiceAccessor.getInstance(project)
            val fileScopedServers = try {
                accessor.getLanguageServers(myFixture.file, allowAll, allowAll)
                    .get(3, TimeUnit.SECONDS)
            } catch (_: Exception) {
                emptyList<Any>()
            }
            if (fileScopedServers.isNotEmpty()) {
                fileScopedServers
            } else {
                val globalServers = accessor.getLanguageServers(allowAll, allowAll).get(3, TimeUnit.SECONDS)
                if (globalServers.isNotEmpty()) globalServers else getStartedLanguageServerItems()
            }
        } catch (_: Exception) {
            emptyList<Any>()
        }
    }

    private fun getStartedLanguageServerItems(): List<LanguageServerItem> {
        val accessor = LanguageServiceAccessor.getInstance(project)
        val wrappers = accessor.getStartedServers().toList()
        return wrappers.mapNotNull { wrapper ->
            if (wrapper.isDisposed) return@mapNotNull null
            val server = wrapper.getLanguageServer() ?: try {
                wrapper.getInitializedServer().get(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
            if (server == null) null else LanguageServerItem(server, wrapper)
        }
    }

    private fun extractWorkspaceEdits(renameDataList: List<*>): List<WorkspaceEdit> {
        return renameDataList.mapNotNull { renameData ->
            when (renameData) {
                is WorkspaceEditData -> renameData.edit()
                else -> invokeAccessor(renameData, "edit") as? WorkspaceEdit
            }
        }
    }

    private fun requestFeatureList(
        support: Any,
        methodName: String,
        params: Any,
        timeoutMillis: Long = 500
    ): List<*> {
        val method = support.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 1
        } ?: return emptyList<Any?>()
        val future = method.invoke(support, params) as? java.util.concurrent.CompletableFuture<*>
            ?: return emptyList<Any?>()
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS) as? List<*> ?: emptyList<Any>()
    }

    private fun invokeAccessor(target: Any?, accessorName: String): Any? {
        if (target == null) return null
        val method = target.javaClass.methods
            .firstOrNull { it.name == accessorName && it.parameterCount == 0 }
            ?: target.javaClass.declaredMethods
                .firstOrNull { it.name == accessorName && it.parameterCount == 0 }
            ?: return null
        method.trySetAccessible()
        return method.invoke(target)
    }

    private fun rangeContains(range: Range, position: Position): Boolean {
        return comparePosition(position, range.start) >= 0 && comparePosition(position, range.end) <= 0
    }

    private fun comparePosition(a: Position, b: Position): Int {
        if (a.line != b.line) return a.line.compareTo(b.line)
        return a.character.compareTo(b.character)
    }

    private fun registerLongRunningAnalyzerThreads(executablePath: String) {
        val commandThreadPrefix = "$executablePath "
        ThreadLeakTracker.longRunningThreadCreated(
            testRootDisposable,
            commandThreadPrefix,
            "BaseDataReader: error stream of $commandThreadPrefix",
            "BaseDataReader: output stream of $commandThreadPrefix"
        )
    }

    private fun stopLanguageServers() {
        if (project.isDisposed) return
        val wrappers = try {
            LanguageServiceAccessor.getInstance(project).getStartedServers().toList()
        } catch (_: Throwable) {
            emptyList()
        }
        if (wrappers.isEmpty()) return
        wrappers.forEach { wrapper ->
            try {
                wrapper.dispose(true)
            } catch (_: Throwable) {
                wrapper.stopAndDisable()
                wrapper.dispose()
            }
        }
        try {
            runWithInvocationEventsDispatching(
                errorMessage = "Timed out waiting for real LSP servers shutdown",
                retries = 500
            ) {
                wrappers.all { it.isDisposed }
            }
        } catch (_: Throwable) {
            // Ignore shutdown races during fixture disposal.
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
