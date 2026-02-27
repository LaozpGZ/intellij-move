# LSP Migration Plan (Move Analyzer)

Date: 2026-02-27  
Branch: `refactor/lsp-migration`

## 1. 目标

将 IntelliJ Move 插件从“本地语义实现”迁移到“`move-analyzer` 统一语义引擎”：

- Move 文件仅保留词法语法高亮。
- 补全、诊断、跳转、悬停等语义能力统一走 LSP。
- 非语义模块（CLI、ToolWindow、Run Config）保持不动。

## 2. 已锁定决策

- LSP 客户端方案：`LSP4IJ`
- 迁移策略：一次性切换（不保留双轨）
- 语法层范围：仅 Lexer/SyntaxHighlighter
- 功能边界：Move 语义能力全部交给 `move-analyzer`

## 3. 实施项

### 3.1 依赖与接入层

- 在构建中引入 `com.redhat.devtools.lsp4ij:0.19.2`
- `plugin.xml` 增加：
  - `<depends>com.redhat.devtools.lsp4ij</depends>`
  - `com.redhat.devtools.lsp4ij.server` 注册 `suiMoveAnalyzer`
  - `com.redhat.devtools.lsp4ij.languageMapping` 绑定 `Sui Move`

### 3.2 move-analyzer 解析与启动

- 新增 `MoveAnalyzerPathResolver`
  - 路径优先级：项目配置 -> PATH -> 常见默认目录
- 新增 `MoveAnalyzerCommandProvider`
  - 统一 `--stdio` 启动参数
  - 自动设置工作目录（Move project root / project base path）
- 新增 `MoveAnalyzerLanguageServerFactory`
  - 接入 LSP4IJ
  - 支持按设置启停

### 3.3 设置界面

- `PerProjectSuiConfigurable` 增加 `Move Analyzer (LSP)` 分组：
  - 启用开关
  - 可执行路径
  - 版本解析展示
- `MvProjectSettingsService` 增加：
  - `moveAnalyzerEnabled`
  - `moveAnalyzerPath`

### 3.4 旧语义链路下线（Move）

`plugin.xml` 已移除/关闭：
- Move completion contributors
- Move annotators/highlighting pass
- Move local inspections
- Move parameter info / inlay hints

保留：
- Move `SyntaxHighlighter`
- Move parser/formatter/refactoring 等非本次迁移核心项

### 3.5 测试策略（迁移期）

- 默认测试策略：`./gradlew test` 仅验证“LSP 迁移后仍应稳定的能力”。
- 旧本地语义测试（annotator/inspection/completion/hints/docs 等）默认排除。
- 如需回看旧语义行为，可显式执行：
  - `./gradlew test -PincludeLegacySemanticTests=true`

## 4. 验证结果

- `./gradlew compileKotlin`：通过
- `./gradlew buildPlugin`：通过
- `./gradlew test --tests "org.sui.ide.lsp.MoveAnalyzerLspIntegrationTest"`：通过（`2 passed`）
- `./gradlew test`（迁移默认模式）：通过（`937 passed`）
- 第一批 LSP 集成测试已完成：
  - `src/test/kotlin/org/sui/ide/lsp/FakeMoveAnalyzerServer.kt`
  - `src/test/kotlin/org/sui/ide/lsp/MoveAnalyzerLspIntegrationTest.kt`
  - 覆盖能力：`diagnostics`、`go-to-definition`
- 详细执行日志见：
  - `docs/logs/lsp/lsp-migration-logs.md`
  - `docs/logs/lsp/lsp-integration-tests.log`
  - `docs/logs/lsp/lsp-migration-build.log`
  - `docs/logs/lsp/lsp-migration-compile.log`
  - `docs/logs/lsp/lsp-migration-test.log`

## 5. 后续待办（下一阶段）

- 在真实 Sui Move 项目中做手工回归：
  - completion / diagnostics / go-to-definition / hover
- 增加第二批 LSP 集成测试：completion / hover
- 增加第三批 LSP 集成测试：references / rename
- 对“高亮小问题”继续做词法 token 映射精修
- 视反馈决定是否进一步收敛本地 refactoring/navigation 扩展点
