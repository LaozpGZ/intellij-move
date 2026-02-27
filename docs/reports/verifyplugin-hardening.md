# verifyPlugin Hardening 记录（2026-02-22）

## 1. 固化验证坐标与安装方式

当前已固定并通过验证的坐标如下：

| shortPlatformVersion | verifierIdeVersion | verifierUseInstaller | 验证命令 |
| --- | --- | --- | --- |
| 253 | `IU-253.31033.19` | `true` | `./gradlew verifyPlugin --no-daemon` |
| 261 | `IU-261.21525.39` | `true` | `ORG_GRADLE_PROJECT_shortPlatformVersion=261 ./gradlew verifyPlugin --no-daemon` |

配置来源：
- `gradle-253.properties`
- `gradle-261.properties`
- `build.gradle.kts` 中 `pluginVerification.ides` 读取 `verifierIdeVersion/verifierUseInstaller`

## 2. INTERNAL_API_USAGES 清单（历史债务 -> 已清零）

> 基线日期：2026-02-22  
> 基线来源：`build/reports/pluginVerifier/**/internal-api-usages.txt`（收紧前）

| ID | 来源 | 引用位置 | 替代方案 | Owner | 状态 |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `BaseState.__getProperties()` (Internal) | `org.sui.cli.settings.MvProjectSettingsService.isDefaultValue` | 改为与 `MoveProjectSettings()` 默认实例做属性值比较 | settings | 已移除 |
| INT-002 | `StoredProperty.isEqualToDefault()` (Internal) | `org.sui.cli.settings.MvProjectSettingsService.isDefaultValue` | 同 INT-001 | settings | 已移除 |
| INT-003 | `DiagnosticBundle` (Internal class) | `org.sui.cli.sentryReporter.onSuccess` | 使用插件自定义文案（避免依赖 internal bundle） | sentry-reporter | 已移除 |
| INT-004 | `DiagnosticBundle.message(...)` (Internal method) | `org.sui.cli.sentryReporter.onSuccess` | 同 INT-003 | sentry-reporter | 已移除 |

收敛结果：
- 253 校验结果：internal usage = 0
- 261 校验结果：internal usage = 0

## 3. 分阶段收紧 failure level 计划（预算化治理）

### 阶段 A（已完成，2026-02-22）
- `INTERNAL_API_USAGES` 不再豁免（默认失败）。
- 保持对以下级别暂时豁免：
  - `DEPRECATED_API_USAGES`
  - `EXPERIMENTAL_API_USAGES`
  - `SCHEDULED_FOR_REMOVAL_API_USAGES`
- 增加配置开关：
  - `verifierAllowInternalApiUsages=false`（默认）

### 阶段 B（执行中，目标日期：2026-03-31）
- 对 `SCHEDULED_FOR_REMOVAL_API_USAGES` 建立预算并逐步降到 0。
- 预算口径：先按基线冻结（防回归），随后逐步下调阈值。
- 基线（2026-02-22）：
  - `253`：`scheduled <= 12`
  - `261`：`scheduled <= 12`
- 建议策略：先预算门禁，再移除豁免。

### 阶段 C（执行中，目标日期：2026-05-31）
- 对 `DEPRECATED_API_USAGES` 建立预算并持续下降。
- 预算口径：先按基线冻结（防回归），随后按热点分批压降。
- 基线（2026-02-22）：
  - `253`：`deprecated <= 28`
  - `261`：`deprecated <= 38`
- 评估 `EXPERIMENTAL_API_USAGES` 的必要性，收敛高风险调用点。

## 4. CI / PR 报告摘要机制

新增：
- `.github/scripts/plugin-verifier-summary.sh`
- `.github/workflows/check.yml` 在 `verifyPlugin` 后追加：
  - 报告摘要输出（写入日志与 `GITHUB_STEP_SUMMARY`）
  - 上传 `build/reports/pluginVerifier/` artifact
  - usage budget 守门（支持 strict / warn 双模式）

预算变量：
- `PLUGIN_VERIFIER_MAX_INTERNAL_API_USAGES`
- `PLUGIN_VERIFIER_MAX_SCHEDULED_FOR_REMOVAL_API_USAGES`
- `PLUGIN_VERIFIER_MAX_DEPRECATED_API_USAGES`
- `PLUGIN_VERIFIER_BUDGET_ENFORCEMENT`（`strict` / `warn`）

双轨门禁策略（2026-02-22 起）：
- `253` 主线：`strict`（超预算失败）
- `261` 预验证：`warn`（超预算告警，不阻断）

这样 PR 中可以直接看到 verifier 摘要，并且在 `strict` 模式下受管预算项出现回归会在 CI 直接失败。

## 5. 首批压降热点（阶段 C 输入清单）

来源：`build/reports/pluginVerifier/IU-261.21525.39/plugins/org.sui.lang/1.6.2.261/deprecated-usages.txt`

高频热点（按出现频次优先）：
- `com.intellij.openapi.application.ActionsKt.runReadAction(...)`
- `com.intellij.util.containers.ConcurrentWeakKeySoftValueHashMap`
- `com.intellij.openapi.actionSystem.UiCompatibleDataProvider.getData(...)`
- `com.intellij.execution.process.ProcessAdapter`
- `com.intellij.util.io.Decompressor` 的旧构造与 `extract(...)`

建议批次：
1. 批次 1（低风险优先）：`runReadAction`、`UiCompatibleDataProvider`、`ProcessAdapter`
2. 批次 2（中风险）：`Decompressor` 相关调用
3. 批次 3（需要回归覆盖）：缓存容器替换（`ConcurrentWeakKeySoftValueHashMap`）

### 阶段 C 批次 1 完成记录（2026-02-22）

- 已替换：
  - `ActionsKt.runReadAction(...)`
  - `UiCompatibleDataProvider.getData(...)` 覆盖点
  - `ProcessAdapter` 继承点
- 验证结果：
  - `./gradlew verifyPlugin --no-daemon`（253）：`scheduled=12, deprecated=20, experimental=119`
  - `ORG_GRADLE_PROJECT_shortPlatformVersion=261 ./gradlew verifyPlugin --no-daemon`（261）：`scheduled=12, deprecated=21, experimental=119`
- 与基线对比（2026-02-22 基线）：
  - `253 deprecated`：`28 -> 20`（下降 8）
  - `261 deprecated`：`38 -> 21`（下降 17）

### 阶段 C 批次 2 完成记录（2026-02-22）

- 已替换：
  - `Decompressor.Zip(File)` -> `Decompressor.Zip(Path)`
  - `Decompressor.Tar(File)` -> `Decompressor.Tar(Path)`
  - `extract(File)` -> `extract(Path)`
- 变更位置：
  - `src/main/kotlin/org/sui/cli/sdks/DownloadAptosSdkTask.kt`
  - `src/main/kotlin/org/sui/cli/sdks/DownloadSuiSdkTask.kt`
- 验证结果：
  - `./gradlew verifyPlugin --no-daemon`（253）：`scheduled=12, deprecated=16, experimental=119`
  - `ORG_GRADLE_PROJECT_shortPlatformVersion=261 ./gradlew verifyPlugin --no-daemon`（261）：`scheduled=12, deprecated=17, experimental=119`
- 与批次 1 对比：
  - `253 deprecated`：`20 -> 16`（再下降 4）
  - `261 deprecated`：`21 -> 17`（再下降 4）
