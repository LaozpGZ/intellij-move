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
        
        def publish_diagnostics(uri, text):
            target = "broken"
            idx = text.find(target)
            diagnostics = []
            if idx >= 0:
                line, char = to_line_char(text, idx)
                diagnostics.append({
                    "range": {
                        "start": {"line": line, "character": char},
                        "end": {"line": line, "character": char + len(target)}
                    },
                    "severity": 1,
                    "source": "fake-move-analyzer",
                    "message": "fake lsp diagnostic"
                })
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
        
            if method == "textDocument/definition":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")
        
                result = []
                marker = "fun target"
                marker_idx = text.find(marker)
                if marker_idx >= 0 and uri:
                    name_idx = text.find("target", marker_idx)
                    if name_idx >= 0:
                        line, char = to_line_char(text, name_idx)
                        result = [{
                            "uri": uri,
                            "range": {
                                "start": {"line": line, "character": char},
                                "end": {"line": line, "character": char + len("target")}
                            }
                        }]
        
                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/references":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")

                result = find_symbol_locations(uri, text, "target") if uri else []
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

                locations = find_symbol_locations(uri, text, "target") if uri else []
                result = None
                if locations:
                    result = {
                        "range": locations[0]["range"],
                        "placeholder": "target"
                    }
                send({"jsonrpc": "2.0", "id": message["id"], "result": result})
                continue

            if method == "textDocument/rename":
                params = message.get("params", {})
                td = params.get("textDocument", {})
                uri = td.get("uri")
                text = open_docs.get(uri, "")
                new_name = params.get("newName", "renamed_target")

                locations = find_symbol_locations(uri, text, "target") if uri else []
                text_edits = [{
                    "range": loc["range"],
                    "newText": new_name
                } for loc in locations]

                result = {"changes": {uri: text_edits}} if uri else {"changes": {}}
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
