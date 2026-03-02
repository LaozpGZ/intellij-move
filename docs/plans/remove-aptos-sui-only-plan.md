# Remove Aptos, Keep Sui-only Plan

Date: 2026-03-02
Branch: `refactor/remove-aptos-sui-only-2026-03-02`

## 1. 目标

将插件从历史的 Sui/Aptos 混合实现收敛为 Sui-only：

- 删除 Aptos 运行配置、设置页、SDK 下载与工具窗口实现。
- 清理 Aptos 相关测试基建与测试样例，避免误导和维护成本。
- 同步修正文档与协作索引，确保后续协作者不再按 Aptos 方向扩展。

## 2. 范围

### 2.1 代码与资源

- 删除 `src/main` 中 Aptos 专属实现（run configurations/settings/sdk/toolwindow/icons/openapiext）。
- 清理 `plugin.xml` 中 Aptos 入口与注册项。
- 统一外部 linter 消息模型为 Sui 命名（`SuiCompilerMessage`）。

### 2.2 测试与样例

- 删除失效的 Aptos 运行配置相关测试。
- 将测试中的 Aptos 命名地址、依赖名、示例 URL 迁移为中性/Sui 语义样例。
- 更新 parser 快照文件中相关标识符，保证快照与输入一致。

### 2.3 文档

- 更新 `docs/README.md`、`todolist.md`。
- 记录本次执行日志和验证状态。

## 3. 非目标

- 不处理 `docs/` 中历史报告/历史计划里对 Aptos 的历史性描述（仅作追溯材料）。
- 不在本阶段改动 LSP 架构与既有抖动治理策略。

## 4. 风险与应对

- 风险：命名地址替换导致 resolve 测试锚点偏移。
  - 应对：优先保留测试语义，只替换标识符；失败后定向修复 caret 对齐。
- 风险：LSP 集成测试存在既有非确定性失败。
  - 应对：单测定向复验，并在日志中标注为既有抖动。

## 5. 验证清单

- [x] `./gradlew compileKotlin`
- [ ] `./gradlew test`（全量受既有 LSP 抖动影响，需看日志说明）
- [x] 定向回归：`ResolveModulesTest` / `ResolveVariablesTest` / LSP 抖动用例单测
- [x] `rg "Aptos|aptos|APTOS"` 代码与测试目录归零（历史 docs 白名单除外）

## 6. 完成标准（DoD）

- Aptos 代码路径与扩展入口删除完成。
- 代码可编译，关键定向测试通过。
- 文档链路（plan/log/todo/readme）已同步。
