package org.sui.ide.lsp

import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionParams
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionParams
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceParams
import com.redhat.devtools.lsp4ij.features.rename.WorkspaceEditData
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.usages.LocationData
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.sui.cli.settings.moveSettings
import org.sui.utils.tests.MvProjectTestBase
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class MoveAnalyzerLspIntegrationTest : MvProjectTestBase() {
    override fun tearDown() {
        try {
            stopLanguageServers()
        } finally {
            super.tearDown()
        }
    }

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

    fun `test unresolved symbol diagnostics are reported from move analyzer`() {
        configureMoveAnalyzer()
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

        val diagnostic = waitForLspDiagnostic("fake unresolved symbol diagnostic")
        val expectedStart = myFixture.file.text.indexOf("missing_symbol")
        check(expectedStart >= 0) { "Failed to locate `missing_symbol` in source" }
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

    fun `test goto definition resolves fully-qualified function call`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        public fun compute_value() {}

                        fun call() {
                            0x1::main::compute/*caret*/_value();
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

        val markerOffset = myFixture.file.text.indexOf("fun compute_value")
        check(markerOffset >= 0) { "Failed to locate declaration marker `fun compute_value` in source" }
        val targetOffset = myFixture.file.text.indexOf("compute_value", markerOffset)
        check(targetOffset >= 0) { "Failed to locate declaration `compute_value` in source" }
        val expectedPosition = LSPIJUtils.toPosition(targetOffset, myFixture.editor.document)

        check(location.range.start.line == expectedPosition.line) {
            "Unexpected definition line: ${location.range.start.line}, expected: ${expectedPosition.line}"
        }
        check(location.range.start.character == expectedPosition.character) {
            "Unexpected definition character: ${location.range.start.character}, expected: ${expectedPosition.character}"
        }
    }

    fun `test completion uses move analyzer result`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun call() {
                            tar/*caret*/
                        }
                    }
                    """
                )
            }
        }

        val context = currentCaretContext()
        val params = LSPCompletionParams(
            TextDocumentIdentifier(context.uri),
            context.position,
            context.offset,
            "",
            false
        )
        val completionLabels = waitForCompletionLabels(params)

        check("target" in completionLabels) {
            "Expected completion item `target`, got $completionLabels"
        }
    }

    fun `test hover uses move analyzer result`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}
                        
                        fun call() {
                            target/*caret*/();
                        }
                    }
                    """
                )
            }
        }

        val context = currentCaretContext()
        val params = LSPHoverParams(
            TextDocumentIdentifier(context.uri),
            context.position,
            context.offset
        )
        val hoverTexts = waitForHoverTexts(params)

        check(hoverTexts.any { it.contains("fake hover from move-analyzer") }) {
            "Expected hover text from fake analyzer, got $hoverTexts"
        }
    }

    fun `test references use move analyzer result`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}
                        fun compute_value() {}
                        
                        fun call() {
                            target();
                            compute_value();
                            compute/*caret*/_value();
                        }
                    }
                    """
                )
            }
        }

        val context = currentCaretContext()
        val params = LSPReferenceParams(
            TextDocumentIdentifier(context.uri),
            context.position,
            context.offset
        )
        val references = waitForReferences(params)

        check(references.size == 3) {
            "Expected exactly 3 references (1 declaration + 2 usages), got ${references.size}: $references"
        }

        check(references.all { reference ->
            rangeText(reference.location().range) == "compute_value"
        }) {
            "Expected references to point to `compute_value` only, got $references"
        }

        val declarationOffset = myFixture.file.text.indexOf("fun compute_value")
        check(declarationOffset >= 0) { "Failed to locate declaration marker `fun compute_value` in source" }
        val symbolOffset = myFixture.file.text.indexOf("compute_value", declarationOffset)
        check(symbolOffset >= 0) { "Failed to locate declaration `compute_value` in source" }
        val declarationPosition = LSPIJUtils.toPosition(symbolOffset, myFixture.editor.document)

        check(references.any {
            val range = it.location().range
            range.start.line == declarationPosition.line && range.start.character == declarationPosition.character
        }) {
            "Expected references to include declaration location"
        }
    }

    fun `test rename uses move analyzer workspace edit`() {
        configureMoveAnalyzer()
        testProject {
            namedMoveToml("SuiPackage")
            sources {
                main(
                    """
                    module 0x1::main {
                        fun target() {}
                        fun compute_value() {}
                        
                        fun call() {
                            target();
                            compute/*caret*/_value();
                            compute_value();
                        }
                    }
                    """
                )
            }
        }

        val newName = "renamed_compute_value"
        val uri = currentCaretContext().uri
        val workspaceEdits = waitForRenameEdits(newName)
        val textEdits = workspaceEdits
            .flatMap { it.changes?.get(uri).orEmpty() }

        check(textEdits.size == 3) {
            "Expected rename edits for declaration + 2 usages, got ${textEdits.size}: $textEdits"
        }
        check(textEdits.all { it.newText == newName }) {
            "Expected all rename edits to use `$newName`, got $textEdits"
        }
        check(textEdits.all { textEdit ->
            rangeText(textEdit.range) == "compute_value"
        }) {
            "Expected rename edits to target `compute_value` only, got $textEdits"
        }
    }

    private fun configureMoveAnalyzer() {
        val executable = FakeMoveAnalyzerServer.createExecutable()
        project.moveSettings.modifyTemporary(testRootDisposable) {
            it.moveAnalyzerEnabled = true
            it.moveAnalyzerPath = executable.toString()
        }
    }

    private data class CaretContext(val uri: String, val offset: Int, val position: Position)

    private fun currentCaretContext(): CaretContext {
        val uri = LSPIJUtils.toUriAsString(myFixture.file)
            ?: error("Failed to build file URI for `${myFixture.file.name}`")
        val offset = myFixture.caretOffset
        val position = LSPIJUtils.toPosition(offset, myFixture.editor.document)
        return CaretContext(uri, offset, position)
    }

    private fun waitForLspDiagnostic(description: String): Diagnostic {
        val fileUri = LSPIJUtils.toUri(myFixture.file)
        var found: Diagnostic? = null
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP diagnostic `$description`",
            retries = 4000
        ) {
            triggerLspRequest()
            LanguageServiceAccessor.getInstance(project).processLanguageServers(myFixture.file) { wrapper ->
                val openedDocument = wrapper.getOpenedDocument(fileUri) ?: return@processLanguageServers
                found = openedDocument.diagnostics.firstOrNull { it.message == description } ?: found
            }
            found != null
        }
        return found ?: error("LSP diagnostic `$description` was not produced")
    }

    private fun triggerLspRequest() {
        val context = currentCaretContext()
        val params = LSPDefinitionParams(TextDocumentIdentifier(context.uri), context.position, context.offset)

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

    private fun waitForReferences(params: LSPReferenceParams): List<LocationData> {
        triggerLspRequest()
        var result: List<LocationData> = emptyList()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP references result",
            retries = 1200
        ) {
            val future = LSPFileSupport.getSupport(myFixture.file).referenceSupport.getReferences(params)
            result = try {
                future.get(250, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                emptyList()
            }
            if (result.isEmpty()) {
                triggerLspRequest()
            }
            result.isNotEmpty()
        }
        return result
    }

    private fun waitForRenameEdits(newName: String): List<WorkspaceEdit> {
        triggerLspRequest()
        var edits: List<WorkspaceEdit> = emptyList()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP rename result",
            retries = 1200
        ) {
            val renameParams = createRenameParams(newName)
            val renameData = try {
                if (renameParams == null) {
                    emptyList<Any>()
                } else {
                    requestFeatureList(
                        support = LSPFileSupport.getSupport(myFixture.file).renameSupport,
                        methodName = "getRename",
                        params = renameParams,
                        timeoutMillis = 250
                    )
                }
            } catch (_: Exception) {
                emptyList<Any>()
            }
            edits = extractWorkspaceEdits(renameData)
            if (edits.isEmpty()) {
                triggerLspRequest()
            }
            edits.isNotEmpty()
        }
        return edits
    }

    private fun waitForCompletionLabels(params: LSPCompletionParams): List<String> {
        triggerLspRequest()
        var labels: List<String> = emptyList()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP completion result",
            retries = 1200
        ) {
            val completionData = try {
                requestFeatureList(
                    support = LSPFileSupport.getSupport(myFixture.file).completionSupport,
                    methodName = "getCompletions",
                    params = params,
                    timeoutMillis = 250
                )
            } catch (_: Exception) {
                emptyList<Any>()
            }
            labels = extractCompletionLabels(completionData)
            if (labels.isEmpty()) {
                triggerLspRequest()
            }
            labels.isNotEmpty()
        }
        return labels
    }

    private fun extractCompletionLabels(completionDataList: List<*>): List<String> {
        return completionDataList.flatMap { completionData ->
            val completionEither = invokeAccessor(completionData, "completion")
                ?: return@flatMap emptyList()
            extractCompletionLabelsFromEither(completionEither)
        }
    }

    private fun waitForHoverTexts(params: LSPHoverParams): List<String> {
        triggerLspRequest()
        var texts: List<String> = emptyList()
        runWithInvocationEventsDispatching(
            errorMessage = "Timed out waiting for LSP hover result",
            retries = 1200
        ) {
            val hoverData = try {
                requestFeatureList(
                    support = LSPFileSupport.getSupport(myFixture.file).hoverSupport,
                    methodName = "getHover",
                    params = params,
                    timeoutMillis = 250
                )
            } catch (_: Exception) {
                emptyList<Any>()
            }
            texts = extractHoverTexts(hoverData)
            if (texts.isEmpty()) {
                triggerLspRequest()
            }
            texts.isNotEmpty()
        }
        return texts
    }

    private fun extractHoverTexts(hoverDataList: List<*>): List<String> {
        return hoverDataList.flatMap { hoverData ->
            val hover = invokeAccessor(hoverData, "hover") ?: return@flatMap emptyList()
            val contents = hover.javaClass.methods
                .firstOrNull { it.name == "getContents" && it.parameterCount == 0 }
                ?.invoke(hover)
                ?: return@flatMap emptyList()
            extractHoverTextsFromEither(contents)
        }
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
        val accessor = LanguageServiceAccessor.getInstance(project)
        val allowAll = Predicate<LSPClientFeatures> { true }
        val serversFuture = accessor.getLanguageServers(
            myFixture.file,
            allowAll,
            allowAll
        )
        return try {
            serversFuture.get(1, TimeUnit.SECONDS)
        } catch (_: Exception) {
            emptyList<Any>()
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

    private fun extractCompletionLabelsFromEither(completionEither: Any): List<String> {
        val left = eitherLeft(completionEither)
        if (left is List<*>) {
            return left.mapNotNull { completionItem ->
                completionItem?.javaClass?.methods
                    ?.firstOrNull { it.name == "getLabel" && it.parameterCount == 0 }
                    ?.invoke(completionItem) as? String
            }
        }

        val right = eitherRight(completionEither) ?: return emptyList()
        val items = right.javaClass.methods
            .firstOrNull { it.name == "getItems" && it.parameterCount == 0 }
            ?.invoke(right) as? List<*>
            ?: return emptyList()
        return items.mapNotNull { completionItem ->
            completionItem?.javaClass?.methods
                ?.firstOrNull { it.name == "getLabel" && it.parameterCount == 0 }
                ?.invoke(completionItem) as? String
        }
    }

    private fun extractHoverTextsFromEither(contentsEither: Any): List<String> {
        val right = eitherRight(contentsEither)
        if (right != null) {
            return listOfNotNull(extractValue(right))
        }

        val left = eitherLeft(contentsEither) as? List<*> ?: return emptyList()
        return left.mapNotNull { part ->
            val text = eitherLeft(part ?: return@mapNotNull null)
            if (text is String) {
                text
            } else {
                extractValue(eitherRight(part))
            }
        }
    }

    private fun eitherLeft(either: Any): Any? {
        return either.javaClass.methods
            .firstOrNull { it.name == "getLeft" && it.parameterCount == 0 }
            ?.invoke(either)
    }

    private fun eitherRight(either: Any): Any? {
        return either.javaClass.methods
            .firstOrNull { it.name == "getRight" && it.parameterCount == 0 }
            ?.invoke(either)
    }

    private fun extractValue(valueHolder: Any?): String? {
        if (valueHolder == null) return null
        return valueHolder.javaClass.methods
            .firstOrNull { it.name == "getValue" && it.parameterCount == 0 }
            ?.invoke(valueHolder) as? String
    }

    private fun rangeText(range: Range): String {
        val document = myFixture.editor.document

        fun lineStartOffset(line: Int): Int {
            check(line in 0 until document.lineCount) {
                "Line index out of bounds: $line (lineCount=${document.lineCount})"
            }
            return document.getLineStartOffset(line)
        }

        val startOffset = lineStartOffset(range.start.line) + range.start.character
        val endOffset = lineStartOffset(range.end.line) + range.end.character
        check(startOffset in 0..document.textLength && endOffset in 0..document.textLength && endOffset >= startOffset) {
            "Invalid range offsets: start=$startOffset, end=$endOffset, textLength=${document.textLength}"
        }
        return document.charsSequence.subSequence(startOffset, endOffset).toString()
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
            errorMessage = "Timed out waiting for LSP servers shutdown",
            retries = 500
        ) {
            wrappers.all { it.isDisposed }
        }
    }

}
