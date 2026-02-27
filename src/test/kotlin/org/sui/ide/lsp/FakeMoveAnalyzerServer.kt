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
                            "definitionProvider": True
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
        
            if method == "shutdown":
                send({"jsonrpc": "2.0", "id": message["id"], "result": None})
                continue
        
            if method == "exit":
                break
        
            if "id" in message:
                send({"jsonrpc": "2.0", "id": message["id"], "result": None})
    """.trimIndent()
}
