# Docs 导航首页

本页是 `docs/` 的统一导航入口。  
使用顺序建议：先看“快速入口”，再按“主题目录导航”深入。

## 快速入口

- LSP 迁移计划：[`docs/plans/lsp-migration-plan.md`](./plans/lsp-migration-plan.md)
- LSP 迁移日志：[`docs/logs/lsp/lsp-migration-logs.md`](./logs/lsp/lsp-migration-logs.md)
- LSP 原始测试日志：[`docs/logs/lsp/lsp-migration-test.log`](./logs/lsp/lsp-migration-test.log)
- LSP 自动化烟测日志：[`docs/logs/lsp/lsp-lsp-tests.log`](./logs/lsp/lsp-lsp-tests.log)
- LSP 集成测试日志（diagnostics+definition+completion+hover+references+rename）：[`docs/logs/lsp/lsp-integration-tests.log`](./logs/lsp/lsp-integration-tests.log)
- 当前任务板：[`todolist.md`](../todolist.md)
- 路径迁移映射：[`docs/FILE_MAP.md`](./FILE_MAP.md)

## 主题目录导航

### `logs/`

- 日志总目录：[`docs/logs/`](./logs/)
- 日推进日志：[`docs/logs/daily/`](./logs/daily/)
- LSP 专项日志：[`docs/logs/lsp/`](./logs/lsp/)
- LSP 主日志：[`docs/logs/lsp/lsp-migration-logs.md`](./logs/lsp/lsp-migration-logs.md)
- LSP 集成测试日志：[`docs/logs/lsp/lsp-integration-tests.log`](./logs/lsp/lsp-integration-tests.log)

### `plans/`

- 计划总目录：[`docs/plans/`](./plans/)
- LSP 迁移计划：[`lsp-migration-plan.md`](./plans/lsp-migration-plan.md)
- Move 2024 计划：[`move-2024-implementation-plan.md`](./plans/move-2024-implementation-plan.md)
- 历史总 TODO：[`2026-01-todo.md`](./plans/2026-01-todo.md)

### `reports/`

- 报告总目录：[`docs/reports/`](./reports/)
- 验证器治理：[`verifyplugin-hardening.md`](./reports/verifyplugin-hardening.md)
- 质量报告：[`quality-report-2026-02-22.md`](./reports/quality-report-2026-02-22.md)
- 对齐报告：[`quality-report-2026-02-22-sui-alignment.md`](./reports/quality-report-2026-02-22-sui-alignment.md)

### `issues/`

- 问题矩阵与模板：[`docs/issues/`](./issues/)
- 模板总表：[`sui-move-2024-issue-templates.md`](./issues/sui-move-2024-issue-templates.md)
- 剩余缺口矩阵：[`sui-move-2024-remaining-gaps-matrix.md`](./issues/sui-move-2024-remaining-gaps-matrix.md)

### `guides/`

- 指南总目录：[`docs/guides/`](./guides/)
- 协作手册：[`ai-collaboration-playbook.md`](./guides/ai-collaboration-playbook.md)
- 测试检查单：[`testing-checklist.md`](./guides/testing-checklist.md)
- 安装指南：[`install-plugin.md`](./guides/install-plugin.md)

## 其他目录

- 归档文档：[`docs/archive/`](./archive/)
- GitHub issue 原始稿与脚本：[`docs/github-issues/`](./github-issues/)
- README 图片资源：[`docs/static/`](./static/)

## 命名规范

- 日期日志：`YYYY-MM-DD[-NN].md`
- 主题文档：`kebab-case.md`
- 原始日志：保持原名并放入对应专题目录（如 `logs/lsp/`）

## 维护约定

- 文档迁移或重命名后，必须同步更新：
  - [`docs/FILE_MAP.md`](./FILE_MAP.md)
  - 本导航页
  - 仓库内引用路径
- 阶段性输出至少包含：
  - 计划（`docs/plans/...`）
  - 执行日志（`docs/logs/...`）
  - TODO（根目录 `todolist.md`）
