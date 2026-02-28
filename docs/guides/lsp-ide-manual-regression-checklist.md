# LSP IDE 手工回归 Checklist

更新时间：2026-02-28  
适用分支：`refactor/lsp-hardening`

## 1. 目标

- 在真实 IDE 环境验证 LSP 迁移后的核心交互是否稳定：
  - completion
  - diagnostics
  - definition
  - hover
  - references
  - rename
- 验证单项目与多项目 workspace 下的行为一致性。

## 2. 前置条件

- 已安装可执行 `move-analyzer`。
- IntelliJ 插件使用当前分支构建产物。
- IDE 设置中可访问：
  - `Move Analyzer Enabled`
  - `Move Analyzer Path`
- 回归前建议先跑自动化基线：
  - `./gradlew test`
  - `./gradlew testMove2024HighlightMacroSuite`
  - `./gradlew test -PincludeLegacySemanticTests=true --tests "org.sui.ide.inspections.*"`

## 3. 执行顺序

1. 单项目场景（Case 01-10）
2. 多项目 workspace 场景（Case 11-14）
3. 记录结果到模板：`docs/logs/lsp/2026-02-28-ide-manual-regression-template.md`

## 4. Case Matrix

| ID | 场景 | 操作 | 预期 |
|---|---|---|---|
| LSP-MAN-01 | Analyzer 路径命中 | 打开设置页，填入有效 `move-analyzer` 路径 | `Resolved from` 显示正确来源；无报错 |
| LSP-MAN-02 | Diagnostics 基础 | 写入明显 unresolved symbol | 出现诊断；修复后诊断消失 |
| LSP-MAN-03 | Definition 本地函数 | 对本模块函数调用执行跳转定义 | 跳到正确声明位置 |
| LSP-MAN-04 | Definition 全限定调用 | 对 `0x...::M::f` 执行跳转定义 | 跳到目标模块声明或可导航位置 |
| LSP-MAN-05 | Hover | 悬停变量/函数/类型 | 展示类型或签名信息，不空白、不抖动 |
| LSP-MAN-06 | Completion | 在模块名、函数名、类型参数位置触发补全 | 候选可用，排序合理，无明显卡顿 |
| LSP-MAN-07 | References | 对函数名执行查找引用 | 返回声明 + 调用点，数量正确 |
| LSP-MAN-08 | Rename | 对函数/变量执行重命名 | 仅修改目标符号相关位置，无误改 |
| LSP-MAN-09 | Move 2024/宏基础 | 校验 `assert!`、`s.wrap!()`、未知宏调用 | 已知宏无误报；未知宏提示合理 |
| LSP-MAN-10 | 开关热同步 | 关闭再开启 `Move Analyzer Enabled` | 行为即时切换，无需重启 IDE |
| LSP-MAN-11 | 多项目工作目录 | 打开含多个 Move 项目的 workspace | 语言服务可启动，诊断可返回 |
| LSP-MAN-12 | 多项目 Definition | 在不同项目下分别做跳转定义 | 跳转落点与当前项目上下文一致 |
| LSP-MAN-13 | 多项目 Rename 安全性 | 在项目 A 触发 rename | 不应误改项目 B 无关文件 |
| LSP-MAN-14 | 多项目 References | 在项目 A/B 分别查找引用 | 结果边界正确，无跨项目污染 |

## 5. 失败分级

- `P0` 阻塞：LSP 无法启动、definition/rename 完全不可用、误改代码。
- `P1` 高优先：结果不稳定、严重误报漏报、明显性能抖动。
- `P2` 一般：文案问题、偶发 UI 体验问题。

## 6. 通过标准

- Case 01-14 全覆盖，`P0=0`。
- 若存在 `P1/P2`，必须在日志中给出复现步骤和建议修复方向。
- 执行后同步更新：
  - `docs/logs/lsp/lsp-migration-logs.md`
  - `todolist.md`

