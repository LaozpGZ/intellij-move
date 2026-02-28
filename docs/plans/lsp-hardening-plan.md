# LSP Hardening Plan (Move Analyzer)

Date: 2026-02-28  
Branch: `refactor/lsp-hardening`

## 1. 目标

在已完成 LSP 迁移的基础上，补齐“可观测性 + 设置热生效 + 多项目确定性行为 + 回归测试”。

本轮不做大范围历史语义代码删除，只做最小风险硬化。

## 2. 已锁定范围

- 保持 `LanguageServerFactory` 架构不变
- 保持 `move-analyzer` 参数兼容策略不变：
  - `sui-move-analyzer` -> `--stdio`
  - `move-analyzer` -> 无附加参数
- 不改 migration gate（`includeLegacySemanticTests` / `includeRealMoveAnalyzerTests`）

## 3. 实施项

### 3.1 路径解析结构化

- `MoveAnalyzerPathResolver` 新增：
  - `ResolutionResult(path, source)`
  - `ResolutionSource`：
    - `CONFIGURED`
    - `PATH`
    - `CARGO_HOME`
    - `SUI_HOME`
    - `UNRESOLVED`
- 保留原有 `resolveExecutable(...)` 出参兼容。
- 增加低噪音日志（仅解析结果变化时记录，测试模式关闭）。

### 3.2 命令构建稳定性

- `MoveAnalyzerCommandProvider`：
  - 使用 `resolveDetailed(...)` 获取解析来源并记录决策日志。
  - 工作目录策略改为：
    - 仅一个 Move 项目：该项目根目录
    - 多个 Move 项目：`project.basePath`
    - 无 Move 项目：`project.basePath`

### 3.3 设置热同步

- 新增 `MoveAnalyzerLspSettingsSyncService`（project service）：
  - 监听 `MOVE_SETTINGS_TOPIC`
  - 当 `moveAnalyzerEnabled` / `moveAnalyzerPath` 变化时：
    - 已启用：重启运行中的 `suiMoveAnalyzer` wrapper
    - 已禁用：停止运行中的 `suiMoveAnalyzer` wrapper
  - 单测模式下旁路（避免与测试生命周期冲突）
- 新增 `MoveAnalyzerLspServerController`，集中筛选当前项目的 move-analyzer wrappers。

### 3.4 设置页可观测性

- `PerProjectSuiConfigurable` 新增 `Resolved from` 行。
- 实时显示来源：`Configured path / PATH / ~/.cargo/bin / ~/.sui/bin / Not found`。

### 3.5 测试补强

- `MoveAnalyzerPathResolverTest`：
  - 覆盖 `CONFIGURED/PATH/CARGO_HOME/SUI_HOME/UNRESOLVED`
- `MoveAnalyzerCommandProviderTest`：
  - 覆盖单项目、多项目、无项目工作目录决策
- 新增 `MoveAnalyzerLspSettingsSyncServiceTest`：
  - 覆盖 settings 变更识别规则
- `MoveAnalyzerLspIntegrationTest`：
  - 定义查询等待策略对齐 references（提升抗抖）
  - teardown 停服容错增强（降低销毁竞态导致的假失败）

## 4. 验证清单

- `./gradlew compileKotlin`
- `./gradlew test --tests "org.sui.ide.lsp.MoveAnalyzerPathResolverTest" --tests "org.sui.ide.lsp.MoveAnalyzerCommandProviderTest" --tests "org.sui.ide.lsp.MoveAnalyzerLspSettingsSyncServiceTest" --tests "org.sui.ide.lsp.MoveAnalyzerLanguageServerFactoryTest"`
- `./gradlew test --tests "org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest"`

## 5. 风险与控制

- 风险：测试环境与 LSP wrapper 生命周期竞态导致偶发 teardown 失败。  
  控制：同步服务在单测模式旁路，集成测停服流程容错增强。

- 风险：多 Move 项目下工作目录行为变化影响历史隐式依赖。  
  控制：显式策略 + 单测覆盖 + 后续手工 workspace 回归。

## 6. 验收标准

- 设置变更后无需重启 IDE 即可使 move-analyzer 配置生效（启停/路径切换）
- 设置页可见解析来源，问题定位可追溯
- 工作目录决策可预测且有测试保护
- 硬化变更通过编译与 LSP 定向回归
