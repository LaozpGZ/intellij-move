# IDE Manual Regression Log (LSP)

日期：`2026-02-28`  
执行人：`Codex + User`  
分支：`refactor/lsp-hardening`  
状态：`DONE (LSP-MAN-01 ~ LSP-MAN-14 已完成)`

## 1. 执行范围

- 参考清单：`docs/guides/lsp-ide-manual-regression-checklist.md`
- 本次覆盖：
  - 单项目：`PASS`
  - 多项目 workspace：`PASS`

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
| LSP-MAN-09 | PASS | 宏语义定向用例通过（builtin/custom/method macro 正向 + unknown macro 负向） | `build/test-results/test/TEST-org.sui.ide.inspections.MvUnresolvedReferenceInspectionTest.xml` |
| LSP-MAN-10 | PASS | 设置热同步单测已可执行并通过（enabled/path 变更触发，非相关配置不触发） | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspSettingsSyncServiceTest.xml` |
| LSP-MAN-11 | PASS | `test diagnostics are reported in multi-project workspace for active project` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-12 | PASS | `test goto definition in multi-project workspace resolves active project declaration` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-13 | PASS | `test references in multi-project workspace stay within active project` 通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |
| LSP-MAN-14 | PASS | `test rename in multi-project workspace updates active project only` 通过；首轮出现 teardown 容器销毁竞态，重跑通过 | `build/test-results/test/TEST-org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest.xml` |

## 3. 结论

- 当前阶段：已完成 LSP-MAN-01 ~ LSP-MAN-14（单项目 + 多项目 workspace）。
- 观测项：`LSP-MAN-11~14` 首轮出现一次 teardown 容器销毁竞态（`ContainerDisposedException`），定向重跑后稳定通过。
- 阻塞项：无。
