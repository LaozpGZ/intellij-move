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
- [x] 迁移期测试策略落地（默认排除旧语义测试，可显式恢复）
- [x] 测试校验通过（`./gradlew test`，941/941）
- [x] 新增 LSP 自动化烟测（路径解析/命令构造/启停开关，`5/5`）
- [x] 第一批 LSP 集成测试落地（`diagnostics + definition`，`2/2`）
- [x] 第二批 LSP 集成测试落地（`completion + hover`，`2/2`）
- [x] 第三批 LSP 集成测试落地（`references + rename`，`2/2`）
- [x] 修复官方 `move-analyzer` 启动参数兼容（`move-analyzer` 不再附加 `--stdio`）
- [x] 新增真实 analyzer 回归入口（默认关闭，`-PincludeRealMoveAnalyzerTests=true` 开启）
- [x] 修复真实 analyzer opt-in 模式线程泄漏误判（`ThreadLeakTracker` long-running 白名单）
- [x] 测试基座隔离环境差异（默认关闭 `moveAnalyzerEnabled`，避免本机工具链影响非 LSP 用例）
- [x] diagnostics + definition 第四批扩展覆盖（`missing_symbol` + fully-qualified definition）
- [x] references + rename 第五批扩展覆盖（按 caret symbol，不再硬编码 `target`）
- [x] 全量回归复验通过（`./gradlew test`，946/946）

## 你睡觉期间我会持续维护的上下文锚点

- 计划文档：`docs/plans/lsp-migration-plan.md`
- 执行日志：`docs/logs/lsp/lsp-migration-logs.md`
- 打包原始日志：`docs/logs/lsp/lsp-migration-build.log`
- 编译原始日志：`docs/logs/lsp/lsp-migration-compile.log`
- 测试原始日志：`docs/logs/lsp/lsp-migration-test.log`
- LSP 烟测日志：`docs/logs/lsp/lsp-lsp-tests.log`

## 下一步执行队列

- [ ] 在安装 `move-analyzer` 的环境做 IDE 内手工回归（补全/诊断/跳转/悬停）
- [ ] 在 `-PincludeRealMoveAnalyzerTests=true` 模式下将“definition request”升级为“definition 命中”断言
- [ ] 记录实际回归结论到 `docs/logs/lsp/lsp-migration-logs.md`
- [ ] 根据结果做第二次小修并提交（若有）

## Commit 策略（按仓库规范）

- 使用 Conventional Commits：
  - `refactor(lsp): ...`
  - `docs(lsp): ...`
  - `fix(lsp): ...`
- 每个可验证阶段完成后提交一次，避免大提交难回滚。
