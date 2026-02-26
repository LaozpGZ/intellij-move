# TODO List (LSP Migration)

Last Update: 2026-02-27  
Owner: Codex + User  
Working Branch: `refactor/lsp-migration`

## 当前阶段

- [x] 创建迁移分支 `refactor/lsp-migration`
- [x] 引入 `LSP4IJ` 依赖并接入 `move-analyzer`
- [x] 设置页增加 Move Analyzer 开关与路径
- [x] 下线 Move 本地语义扩展点（completion/annotator/inspection 等）
- [x] 构建校验通过（`compileKotlin` / `buildPlugin`）
- [x] 迁移日志沉淀到 Markdown

## 你睡觉期间我会持续维护的上下文锚点

- 计划文档：`docs/lsp-migration-plan.md`
- 执行日志：`docs/lsp-migration-logs.md`
- 打包原始日志：`docs/lsp-migration-build.log`
- 编译原始日志：`docs/lsp-migration-compile.log`

## 下一步执行队列

- [ ] 在样例项目做 LSP 功能回归（补全/诊断/跳转/悬停）
- [ ] 记录实际回归结论到 `docs/lsp-migration-logs.md`
- [ ] 根据结果做第二次小修并提交（若有）

## Commit 策略（按仓库规范）

- 使用 Conventional Commits：
  - `refactor(lsp): ...`
  - `docs(lsp): ...`
  - `fix(lsp): ...`
- 每个可验证阶段完成后提交一次，避免大提交难回滚。
