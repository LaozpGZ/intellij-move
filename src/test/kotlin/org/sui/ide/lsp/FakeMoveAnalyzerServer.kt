package org.sui.ide.lsp

import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.sui.stdext.getCliFromPATH
import java.nio.file.Files
import java.nio.file.Path

object FakeMoveAnalyzerServer {
    fun createExecutable(): Path {
        Assume.assumeFalse("Windows test host is not supported for fake analyzer script", SystemInfo.isWindows)
        Assume.assumeTrue("python3 is required for fake analyzer script", getCliFromPATH("python3") != null)

        val tempDir = Files.createTempDirectory("fake-move-analyzer")
        val scriptPath = tempDir.resolve("sui-move-analyzer")
        Files.writeString(scriptPath, SCRIPT)
        check(scriptPath.toFile().setExecutable(true)) {
            "Failed to mark fake analyzer script as executable: $scriptPath"
        }
        return scriptPath
    }

    private val SCRIPT = """
        #!/usr/bin/env python3
        import json
        import re
        import sys
        
        open_docs = {}
        
        def read_message():
            headers = {}
            while True:
                line = sys.stdin.buffer.readline()
                if not line:
                    return None
                if line in (b"\r\n", b"\n"):
                    break
                if b":" not in line:
                    continue
                key, value = line.decode("utf-8", errors="replace").split(":", 1)
                headers[key.strip().lower()] = value.strip()
            content_length = int(headers.get("content-length", "0") or "0")
            if content_length <= 0:
                return None
            body = sys.stdin.buffer.read(content_length)
            if not body:
                return None
            return json.loads(body.decode("utf-8", errors="replace"))
        
        def send(payload):
            raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            sys.stdout.buffer.write(f"Content-Length: {len(raw)}\r\n\r\n".encode("ascii"))
            sys.stdout.buffer.write(raw)
            sys.stdout.buffer.flush()
        
        def to_line_char(text, offset):
            line = text.count("\n", 0, offset)
            last_nl = text.rfind("\n", 0, offset)
            char = offset if last_nl == -1 else offset - last_nl - 1
            return line, char

        def to_offset(text, line, char):
            if line < 0 or char < 0:
                return None
            current_line = 0
            current_char = 0
            for idx, c in enumerate(text):
                if current_line == line and current_char == char:
                    return idx
                if c == "\n":
                    current_line += 1
                    current_char = 0
                else:
                    current_char += 1
            if current_line == line and current_char == char:
                return len(text)
            return None

        def symbol_at_position(text, line, char):
            offset = to_offset(text, line, char)
            if offset is None or not text:
                return None
            if offset >= len(text):
                offset = max(0, len(text) - 1)

            def is_ident_char(ch):
                return ch.isalnum() or ch == "_"

            if not is_ident_char(text[offset]):
                if offset == 0 or not is_ident_char(text[offset - 1]):
                    return None
                offset -= 1

            start = offset
            while start > 0 and is_ident_char(text[start - 1]):
                start -= 1

            end = offset + 1
            while end < len(text) and is_ident_char(text[end]):
                end += 1
            return text[start:end]

        def find_symbol_locations(uri, text, symbol):
            locations = []
            cursor = 0
            while True:
                idx = text.find(symbol, cursor)
                if idx < 0:
                    break
                line, char = to_line_char(text, idx)
                locations.append({
                    "uri": uri,
                    "range": {
                        "start": {"line": line, "character": char},
                        "end": {"line": line, "character": char + len(symbol)}
                    }
                })
                cursor = idx + len(symbol)
            return locations

        def find_function_definitions(symbol):
            if not symbol:
                return []
            pattern = re.compile(r"\b(?:public\s+)?(?:entry\s+)?(?:native\s+)?fun\s+" + re.escape(symbol) + r"\b")
            definitions = []
            for uri, text in open_docs.items():
                for match in pattern.finditer(text):
                    name_idx = text.find(symbol, match.start(), match.end())
                    if name_idx < 0:
                        continue
                    line, char = to_line_char(text, name_idx)
                    definitions.append({
                        "uri": uri,
                        "range": {
                            "start": {"line": line, "character": char},
                            "end": {"line": line, "character": char + len(symbol)}
                        }
                    })
            return definitions
        
        def publish_diagnostics(uri, text):
            diagnostics = []
            diagnostic_targets = [
                ("broken", "fake lsp diagnostic"),
                ("missing_symbol", "fake unresolved symbol diagnostic"),
            ]
            for target, message in diagnostic_targets:
                cursor = 0
                while True:
                    idx = text.find(target, cursor)
                    if idx < 0:
                        break
                    line, char = to_line_char(text, idx)
                    diagnostics.append({
                        "range": {
                            "start": {"line": line, "character": char},
                            "end": {"line": line, "character": char + len(target)}
                        },
                        "severity": 1,
                        "source": "fake-move-analyzer",
                        "message": message
                    })
                    cursor = idx + len(target)
            send({
                "jsonrpc": "2.0",
                "method": "textDocument/publishDiagnostics",
                "params": {"uri": uri, "diagnostics": diagnostics}
            })
        
        while True:
            message = read_message()
            if message is None:
                break
        
            method = message.get("method")
            if method == "initialize":
                send({
                    "jsonrpc": "2.0",
                    "id": message["id"],
                    "result": {
                        "capabilities": {
                            "textDocumentSync": 1,
                            "definitionProvider": True,
                            "referencesProvider": True,
                            "hoverProvider": True,
                            "renameProvider": {"prepareProvider": True},
                            "completionProvider": {
                                "resolveProvider": False,
                                "triggerCharacters": [".", ":"]
                            }
                        },
                        "serverInfo": {"name": "fake-move-analyzer", "version": "0.0.1"}
                    }
                })
                continue
        
            if method == "initialized":
                continue
        
            if method == "textDocument/didOpen":
                td = message.get("params", {}).get("textDocument", {})
                uri = td.get("uri")
                text = td.get("text", "")
                if uri:
                    open_docs[uri] = text
                    publish_diagnostics(uri, text)
                continue

            if method == "textDocument/didChange":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                changes = params.get("contentChanges", [])
                if uri and changes:
                    latest_text = changes[-1].get("text", "")
                    open_docs[uri] = latest_text
                    publish_diagnostics(uri, latest_text)
                continue
        
            if method == "textDocument/definition":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                position = params.get("position", {})
                symbol = symbol_at_position(
                    text,
                    position.get("line", 0),
                    position.get("character", 0),
                )
                result = find_function_definitions(symbol)

                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/references":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                position = params.get("position", {})
                symbol = symbol_at_position(
                    text,
                    position.get("line", 0),
                    position.get("character", 0),
                )

                result = []
                if symbol:
                    for opened_uri, opened_text in open_docs.items():
                        result.extend(find_symbol_locations(opened_uri, opened_text, symbol))
                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/completion":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                result = []
                if "tar" in text:
                    result = [{
                        "label": "target",
                        "kind": 3,
                        "detail": "fake completion from move-analyzer",
                        "insertText": "target"
                    }]

                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/hover":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                result = None
                if "target" in text:
                    result = {
                        "contents": {
                            "kind": "markdown",
                            "value": "fake hover from move-analyzer"
                        }
                    }

                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/prepareRename":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                position = params.get("position", {})
                symbol = symbol_at_position(
                    text,
                    position.get("line", 0),
                    position.get("character", 0),
                )
                locations = find_symbol_locations(uri, text, symbol) if uri and symbol else []
                result = None
                if locations:
                    result = {
                        "range": locations[0]["range"],
                        "placeholder": symbol
                    }
                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/rename":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")
                new_name = params.get("newName", "renamed_target")

                position = params.get("position", {})
                symbol = symbol_at_position(
                    text,
                    position.get("line", 0),
                    position.get("character", 0),
                )
                changes = {}
                if symbol:
                    for opened_uri, opened_text in open_docs.items():
                        locations = find_symbol_locations(opened_uri, opened_text, symbol)
                        if not locations:
                            continue
                        changes[opened_uri] = [{
                            "range": loc["range"],
                            "newText": new_name
                        } for loc in locations]

                result = {"changes": changes}
                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue
        
            if method == "shutdown":
                send({"jsonrpc": "2.0", "id": message["id"], "result": None})
                continue
        
            if method == "exit":
                break
        
            if "id" in message:
                send({"jsonrpc": "2.0", "id": message["id"], "result": None})
    """.trimIndent()
}
