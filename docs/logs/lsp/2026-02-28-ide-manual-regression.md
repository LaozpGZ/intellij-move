# IDE Manual Regression Log (LSP)

日期：`2026-02-28`  
执行人：`Codex + User`  
分支：`refactor/lsp-hardening`  
状态：`IN_PROGRESS (LSP-MAN-01 ~ LSP-MAN-08 已完成)`

## 1. 执行范围

- 参考清单：`docs/guides/lsp-ide-manual-regression-checklist.md`
- 本次覆盖：
  - 单项目：`PARTIAL`
  - 多项目 workspace：`PENDING`

## 2. Case 结果

| ID | 结果 (PASS/FAIL/BLOCKED) | 备注 | 证据路径 |
|---|---|---|---|
| LSP-MAN-01 | PASS | 通过 `MoveAnalyzerPathResolverTest` 验证 `CONFIGURED/PATH/CARGO_HOME/SUI_HOME/UNRESOLVED` 路径来源解析 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerPathResolverTest.xml` |
| LSP-MAN-02 | PASS | 通过 `MoveAnalyzerLspIntegrationTest` 诊断用例验证 unresolved symbol diagnostics 返回 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-03 | PASS | 通过 `MoveAnalyzerLspIntegrationTest` 本地函数跳转定义用例验证 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-04 | PASS | 通过 `MoveAnalyzerLspIntegrationTest` fully-qualified function call 跳转定义用例验证 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-05 | PASS | `test hover uses move analyzer result` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-06 | PASS | `test completion uses move analyzer result` 首轮超时，定向重跑后通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-07 | PASS | `test references use move analyzer result` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-08 | PASS | `test rename uses move analyzer workspace edit` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-09 | PENDING |  |  |
| LSP-MAN-10 | PENDING |  |  |
| LSP-MAN-11 | PENDING |  |  |
| LSP-MAN-12 | PENDING |  |  |
| LSP-MAN-13 | PENDING |  |  |
| LSP-MAN-14 | PENDING |  |  |

## 3. 结论

- 当前阶段：已完成 LSP-MAN-01 ~ LSP-MAN-08（单项目核心链路第一批）。
- 阻塞项：无代码阻塞；需要在本地 IDE 中逐项操作并记录证据。
