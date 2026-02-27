# AGENTS Playbook (intellij-move)

本文件定义本仓库的协作执行协议。目标是让任何一次中断都能被快速接力，任何一次改动都可追溯。

## 1. Mission & Scope

- 项目目标：维护 Sui Move IntelliJ 插件，稳定推进语法能力、语义能力与工程质量。
- 交付标准：代码可编译、测试可验证、文档可追溯、上下文可接力。
- 本协议覆盖：
  - 分支与提交节奏
  - 计划/日志/TODO 落盘要求
  - 风险操作确认机制
  - 收尾与 DoD 标准

## 2. Core Collaboration Rules

- 所有阶段性工作必须留下三类文档：
  - 计划：`docs/plans/...`
  - 执行日志：`docs/logs/...`
  - 任务看板：根目录 `todolist.md`
- 任何文档路径迁移或重命名后，必须同步更新：
  - `docs/README.md`
  - `docs/FILE_MAP.md`
  - 仓库内引用路径
- 每个“可验证阶段”完成后必须提交一次，避免大提交难回滚。
- 禁止回滚或覆盖与当前任务无关的脏改动。

## 3. Branch & Commit Policy

- 未经明确要求，不主动新建/切换分支；若用户指定分支，严格按用户分支执行。
- Commit message 必须遵循 Conventional Commits：
  - `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`
- 推荐粒度：
  - 代码变更单独提交
  - 文档与上下文变更单独提交
- 禁止：
  - `git reset --hard`
  - 未经用户确认的强推
  - 未经要求的 amend/rebase 历史改写

## 4. Context Management Protocol

### 4.1 开始前

- 必读锚点：
  - `todolist.md`
  - `docs/README.md`
  - 当前专项计划（如 `docs/plans/lsp-migration-plan.md`）
  - 当前专项日志（如 `docs/logs/lsp/lsp-migration-logs.md`）

### 4.2 执行中

- 任何关键决策都要写入日志（原因、影响、验证方式）。
- 遇到中断风险时，先写“handoff snapshot”再继续。

### 4.3 中断恢复

- 按顺序恢复上下文：
  1. 看 `todolist.md` 的“当前阶段/下一步队列”
  2. 看对应 `docs/logs/...` 最新章节
  3. 看对应 `docs/plans/...` 的目标与验收标准

## 5. Documentation Logging Protocol

- 计划文档要求：
  - 明确目标、范围、风险、验证清单
- 执行日志要求：
  - 记录命令、结果、失败原因、修复动作、最终状态
  - 保留原始输出路径（例如 `docs/logs/lsp/*.log`）
- Handoff 要求：
  - 新增 `Handoff Snapshot` 段，至少包含：
    - 当前分支
    - 已完成项
    - 验证结果
    - 待继续项

## 6. TODO Management Protocol

- 根目录 `todolist.md` 为唯一任务状态入口（single source of truth）。
- 每次阶段完成必须更新：
  - 已完成项勾选
  - 下一步队列
  - 关键上下文锚点路径

## 7. Docs Structure & Naming Rules

- 目录语义：
  - `docs/logs/`：执行日志
  - `docs/plans/`：计划
  - `docs/reports/`：报告
  - `docs/issues/`：问题矩阵/模板
  - `docs/guides/`：使用与流程指南
  - `docs/archive/`：历史沉淀
  - `docs/github-issues/`：issue body 与脚本（保留原路径）
  - `docs/static/`：图片资源
- 命名规范：
  - 日期日志：`YYYY-MM-DD[-NN].md`
  - 主题文档：`kebab-case.md`
- 迁移规则：
  - 所有重命名必须进入 `docs/FILE_MAP.md`

## 8. Execution Checklist

### 8.1 Start

- 对齐分支与目标任务
- 同步 `todolist.md` 状态
- 准备计划文档（若无则先建）

### 8.2 During

- 先读后改，避免盲改
- 每个关键节点写日志
- 及时做最小验证（编译/定向测试）

### 8.3 End

- 更新 `todolist.md`
- 更新 `docs/logs/...`
- 更新 `docs/README.md`（如有新文档）
- 提交并记录 commit id

## 9. Risky Operations Confirmation

执行以下操作前必须获得明确确认：

- 删除/批量移动文件
- `git push`、历史改写、分支重置
- 数据库结构变更或批量数据更新
- 生产环境 API/凭证相关操作
- 全局依赖安装/卸载

## 10. Build & Test Baseline

- 常规最小校验：
  - `./gradlew compileKotlin`
  - `./gradlew test`
- 涉及兼容性或发布前建议：
  - `./gradlew verifyPlugin`
  - `./gradlew buildPlugin`
- LSP 迁移阶段补充约定：
  - 默认测试模式使用迁移闸门
  - 需要回看旧本地语义时显式执行：
    - `./gradlew test -PincludeLegacySemanticTests=true`
  - 需要执行真实 `move-analyzer` 回归时显式执行：
    - `./gradlew test -PincludeRealMoveAnalyzerTests=true --tests "org.sui.ide.lsp.MoveAnalyzerRealLspIntegrationTest"`

## 11. Definition of Done (DoD)

- 代码目标达成且与范围一致（无额外过度设计）
- 编译/测试结果可复现
- 计划/日志/TODO 全部同步
- 新增或迁移文档已入索引并可追溯
- 提交信息符合规范，可被下一个执行者直接接力
