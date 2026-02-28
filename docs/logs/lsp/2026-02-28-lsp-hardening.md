# LSP Hardening Execution Log

Date: 2026-02-28  
Branch: `refactor/lsp-hardening`

## 1. Branch

### Command
```bash
git checkout -b "refactor/lsp-hardening" "refactor/lsp-migration"
```

### Result
- `Switched to a new branch 'refactor/lsp-hardening'`

## 2. Implemented Changes

### 2.1 Resolver hardening

- `src/main/kotlin/org/sui/ide/lsp/MoveAnalyzerPathResolver.kt`
  - 增加 `ResolutionResult(path, source)`
  - 增加 `ResolutionSource`（`CONFIGURED/PATH/CARGO_HOME/SUI_HOME/UNRESOLVED`）
  - 增加低噪音解析日志（仅状态变化记录，单测模式关闭）

### 2.2 Command provider hardening

- `src/main/kotlin/org/sui/ide/lsp/MoveAnalyzerCommandProvider.kt`
  - 工作目录策略显式化：
    - 单 Move 项目：项目根目录
    - 多 Move 项目：`project.basePath`
    - 无 Move 项目：`project.basePath`
  - 增加命令构建决策日志（exec/args/workdir/source）

### 2.3 Runtime settings sync

- `src/main/kotlin/org/sui/ide/lsp/MoveAnalyzerLspSettingsSyncService.kt`（新增）
  - 监听 `MOVE_SETTINGS_TOPIC`
  - 对 `moveAnalyzerEnabled/moveAnalyzerPath` 变更执行 wrapper 启停同步
  - 单测模式旁路，避免测试生命周期冲突

- `src/main/kotlin/org/sui/ide/lsp/MoveAnalyzerLspServerController.kt`（新增）
  - 集中筛选当前项目中 `suiMoveAnalyzer` 运行 wrappers

- `src/main/resources/META-INF/plugin.xml`
  - 注册 `MoveAnalyzerLspSettingsSyncService` 为 `projectService`

### 2.4 Settings UI observability

- `src/main/kotlin/org/sui/cli/settings/PerProjectSuiConfigurable.kt`
  - 增加 `Resolved from` 行
  - 实时显示解析来源并在未命中时红色标记

### 2.5 Tests

- `src/test/kotlin/org/sui/ide/lsp/MoveAnalyzerPathResolverTest.kt`
  - 补齐多级回退来源覆盖

- `src/test/kotlin/org/sui/ide/lsp/MoveAnalyzerCommandProviderTest.kt`
  - 补齐工作目录策略覆盖

- `src/test/kotlin/org/sui/ide/lsp/MoveAnalyzerLspSettingsSyncServiceTest.kt`（新增）
  - 补齐 settings 变更判定覆盖

- `src/test/kotlin/org/sui/ide/lsp/MoveAnalyzerLspIntegrationTest.kt`
  - 强化 definition 等待策略
  - 强化 teardown 停服容错

## 3. Validation

### Compile
```bash
./gradlew compileKotlin
```
- Passed

### LSP targeted tests
```bash
./gradlew test --tests "org.sui.ide.lsp.MoveAnalyzerPathResolverTest" --tests "org.sui.ide.lsp.MoveAnalyzerCommandProviderTest" --tests "org.sui.ide.lsp.MoveAnalyzerLspSettingsSyncServiceTest" --tests "org.sui.ide.lsp.MoveAnalyzerLanguageServerFactoryTest"
```
- Passed

### LSP integration
```bash
./gradlew test --tests "org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest"
```
- Passed（单独运行）

### Note on suite flakiness
- 批量运行 `org.sui.ide.lsp.*` 时仍可见偶发销毁期抖动（`ContainerDisposedException`），本轮已降低但未承诺完全消除；不阻塞本次硬化功能目标。

## 4. Commit Log

- `5beeab66` `fix(lsp): harden analyzer resolution and runtime sync`
  - 代码与测试主提交

## 5. Handoff Snapshot

- 当前分支：`refactor/lsp-hardening`
- 已完成：
  - 路径解析来源结构化
  - 设置页来源可视化
  - 设置变更热同步（启停/路径切换）
  - 工作目录策略确定性化
  - 对应测试补齐
- 待继续：
  - 真实 IDE 手工回归（单项目/多项目）
  - 评估是否继续收敛 `org.sui.ide.lsp.*` 批量跑时偶发销毁期抖动
