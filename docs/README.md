# Docs Index

本文档是 `/docs` 的统一入口，按主题组织，所有新增文档都应先归类再入库。

## 目录结构

- `docs/logs/`
  - `daily/`：按日期记录的推进日志（`YYYY-MM-DD[-NN].md`）
  - `lsp/`：LSP 迁移专项日志与原始构建/测试日志
- `docs/plans/`：实施计划、路线图、待办规划
- `docs/reports/`：质量报告、验证报告、治理报告
- `docs/issues/`：问题矩阵、Issue 模板
- `docs/guides/`：协作手册、安装/更新/验证指引、辅助脚本
- `docs/archive/`：历史沉淀文档
- `docs/github-issues/`：GitHub issue body 与脚本（保持原有语义路径）
- `docs/static/`：README 引用图片资源

## 快速入口

- LSP 迁移计划：`docs/plans/lsp-migration-plan.md`
- LSP 迁移日志：`docs/logs/lsp/lsp-migration-logs.md`
- LSP 原始测试日志：`docs/logs/lsp/lsp-migration-test.log`
- 当前任务板：`todolist.md`
- 历史路径映射：`docs/FILE_MAP.md`

## 命名规范

- 日期日志：`YYYY-MM-DD[-NN].md`
- 主题文档：`kebab-case.md`
- 原始日志：保持原名并放入对应专题目录（如 `logs/lsp/`）

## 维护规则

- 文档迁移或重命名后，必须同步更新：
  - `docs/FILE_MAP.md`
  - 本文件索引
  - 仓库内引用路径
- 阶段性输出至少包含：
  - 计划（`docs/plans/...`）
  - 执行日志（`docs/logs/...`）
  - TODO（根目录 `todolist.md`）
