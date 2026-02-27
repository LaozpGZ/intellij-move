# Sui Move IntelliJ 插件质量检查报告

## 1. 执行摘要

- 审计时间：2026-02-22（CST）
- 审计范围：构建可用性、测试稳定性、JetBrains 平台兼容性（253/261）、工程规范与技术债
- 总体结论：**质量良好，可持续发布，但存在中高优先级兼容性债务与流程门禁缺口**
- 建议总体评级：**B+（7.9/10）**

核心结论：
- `compileKotlin`、`test`、`verifyPlugin` 在 253 基线全部通过。
- 261 预验证（`verifyPluginProjectConfiguration + verifyPlugin`）通过，兼容结论为 Compatible。
- 已识别到明确的 API 迁移压力（deprecated/scheduled/experimental）与 CI 门禁松动点（261 非阻塞）。

## 2. 审计范围与方法

本次采用“硬门禁 + 静态扫描 + 流程检查”：

1. 硬门禁执行：
   - `./gradlew compileKotlin --no-daemon`
   - `./gradlew test --no-daemon`
   - `./gradlew verifyPlugin --no-daemon`（253）
   - `ORG_GRADLE_PROJECT_shortPlatformVersion=261 ./gradlew verifyPluginProjectConfiguration verifyPlugin --no-daemon`
2. 产物审阅：
   - `build/reports/tests/test/index.html`
   - `build/reports/pluginVerifier/IU-261.21525.39/report.html`
   - `build/reports/pluginVerifier/IU-261.21525.39/plugins/org.sui.lang/1.6.2.261/verification-verdict.txt`
3. 静态质量扫描：
   - 文件规模、TODO/FIXME、日志输出、CI 工作流门禁策略

## 3. 结果总览

### 3.1 构建与测试

| 项目 | 结果 | 关键数据 |
|---|---|---|
| `compileKotlin` (253) | 通过 | BUILD SUCCESSFUL，约 7s |
| `test` (253) | 通过 | 1807 tests，0 failures，0 errors，0 skipped |
| 测试报告 | 可用 | `build/reports/tests/test/index.html` |

补充测试统计：
- 测试类（XML）数量：156
- 汇总测试时长（suite time）：约 66.546s
- 最慢测试 Top3：`PrimitiveTypesCompletionTest`、`MvTypeCheckInspectionTest`、`ExpressionTypesTest`

### 3.2 插件兼容性（Plugin Verifier）

| 目标 IDE | 结论 | Scheduled Removal | Deprecated | Experimental |
|---|---|---:|---:|---:|
| IU-253.31033.19 | Compatible | 12 | 16 | 119 |
| IU-261.21525.39 | Compatible | 12 | 17 | 119 |

261 结论来源：`build/reports/pluginVerifier/IU-261.21525.39/plugins/org.sui.lang/1.6.2.261/verification-verdict.txt`

## 4. 发现的问题（按优先级）

### 高优先级（应尽快处理）

1. API 兼容性债务已接近预算上限  
   - 证据：
     - `253` 下 scheduled-for-removal = 12，正好触及 CI 严格预算上限（`.github/workflows/check.yml:20`）。
     - `261` 下 deprecated 进一步上升到 17，总 deprecated usages 为 29。  
   - 主要热点：
     - `org.sui.ide.annotator.RsExternalLinterPass`
     - `org.sui.cli.settings.MvProjectSettingsServiceBase`
     - `org.sui.lang.core.resolve.ref.MvResolveCacheKt`（`ConcurrentWeakKeySoftValueHashMap`）
     - `org.sui.ide.hints.type.MvInlayTypeHintsProvider`（experimental 聚集）

2. 261 预验证流程为“非阻塞 + 警告模式”  
   - 证据：`continue-on-error: true`（`.github/workflows/check.yml:98`），`PLUGIN_VERIFIER_BUDGET_ENFORCEMENT: warn`（`.github/workflows/check.yml:104`）。
   - 风险：EAP 回归可能进入主分支后才被动发现。

### 中优先级（建议本迭代处理）

1. 261 任务未执行自动化测试，仅做 build+verify  
   - 证据：`eap-preverify` 无 `:test` 步骤（参考 `.github/workflows/check.yml:126` 与 `.github/workflows/check.yml:146`）。

2. 生产代码存在直接 `println` 输出  
   - 位置：
     - `src/main/kotlin/org/sui/cli/runConfigurations/sui/Sui.kt:55`
     - `src/main/kotlin/org/sui/ide/actions/GetGasAction.kt:17`
     - `src/main/kotlin/org/sui/ide/actions/GetGasAction.kt:24`
   - 风险：日志不可控、噪声增加、排障上下文弱。

3. 技术债标签数量可见（TODO/FIXME/XXX/HACK = 33）  
   - 分布：`ide` 15、`cli` 12、`lang` 6。

4. 编译警告存在“未来升级将转错误”信号  
   - 典型位置：
     - `src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt:220`（KT-71420）
     - `src/main/kotlin/org/sui/ide/inspections/imports/AutoImportFix.kt:101`（KT-11914）

### 低优先级（持续改进）

1. README 信息老化/小错误  
   - 可见问题：
     - 多余字符：`README.md:17`
     - 本地安装包示例版本陈旧：`README.md:21`
     - 兼容描述仍写“2022.3+”：`README.md:40`（与当前 253/261 策略存在认知偏差）

2. 未看到静态质量门禁任务（detekt/ktlint/jacoco/kover）  
   - 影响：风格、复杂度、覆盖率缺少自动化硬约束。

## 5. 质量评分

| 维度 | 评分 | 说明 |
|---|---:|---|
| 可构建性 | 9.5/10 | 双平台构建/验证成功 |
| 测试稳定性 | 9.0/10 | 1807 用例全绿，无跳过 |
| 平台兼容性 | 7.5/10 | Compatible，但 API 债务较高 |
| 工程规范性 | 7.0/10 | 有技术债与日志规范问题 |
| 交付流程门禁 | 6.5/10 | 261 非阻塞、缺少 261 自动测试 |
| **综合** | **7.9/10** | **B+，建议“可发布但需治理”** |

## 6. 整改建议（30 天）

### P0（1 周内）
- 先清理 scheduled-for-removal API（目标：从 12 降到 <= 8）。
- 重点模块：`RsExternalLinterPass`、`MvResolveCacheKt`、`MoveProjectGeneratorPeer`、`MoveLangProjectOpenProcessor`。

### P1（2 周内）
- 将 261 预验证改为阻塞或至少“失败可见 + 强提醒”。
- 在 `eap-preverify` 增加 `:test`（可先跑关键测试集）。

### P2（本月内）
- 替换生产 `println` 为统一 Logger。
- 建立静态门禁（detekt/ktlint）与覆盖率基线（kover/jacoco）。
- 对 TODO/FIXME 建台账并按模块逐步清理。

## 7. 附录：关键证据路径

- 测试报告：`build/reports/tests/test/index.html`
- 261 验证报告：`build/reports/pluginVerifier/IU-261.21525.39/report.html`
- 261 结论文件：`build/reports/pluginVerifier/IU-261.21525.39/plugins/org.sui.lang/1.6.2.261/verification-verdict.txt`
- CI 策略：`.github/workflows/check.yml`
