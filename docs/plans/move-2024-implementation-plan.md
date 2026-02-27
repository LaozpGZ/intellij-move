# Move 2024 支持落地计划（分阶段）

> 目标：按阶段落地 Move 2024 迁移指南与参考页要求，优先保障解析/PSI/诊断/推断一致性，避免过度设计。

## 里程碑概览

- **P0 基础与配置校验**：保证 `edition` 解析与校验正确，feature gate 与 UI override 可用。
- **P1 语法/PSI 建模补齐**：`use fun`、`public use fun`、`macro fun`、`type` 关键字处理等语法/PSI 完整。
- **P2 解析/推断/诊断完善**：语义解析、引用解析、类型推断、诊断/快速修复。
- **P3 生态与体验**：补全/格式化/高亮/测试覆盖与迁移辅助。

## P0：基础与配置校验（当前已部分完成）

### 目标
- 完成 Move.toml `edition` 解析与合法性校验。
- 将 edition 映射为 feature gate，支持 UI 覆盖。
- 与 Move 2024 迁移指南保持一致（含 2024.beta）。

### 已完成
- `Move.toml` edition 解析与校验。
- `MoveEdition` 与 `MoveLanguageFeatures` 的基础架构。

### 待完成
1. **补齐 edition 值范围**
   - 增加对 `2024.beta` / `move-2024-beta` 的支持。
   - 同步错误提示列表。
   - 参考文件：
     - `src/main/kotlin/org/sui/cli/MovePackage.kt`
     - `src/main/kotlin/org/sui/toml/MoveTomlErrorAnnotator.kt`
2. **统一 feature gate 使用范围**
   - 清理散落的 V2/开关逻辑，统一使用 `MoveLanguageFeatures`。
   - 重点涉及：解析、诊断、推断、补全。

### 验收标准
- `Move.toml` 指定 `edition = "2024.beta"` 时无错误提示。
- UI override 关闭时，特性与 edition 一致。

## P1：语法与 PSI 建模补齐

### 目标
- 使语法与 PSI 能完整表达 Move 2024 关键新增语法。

### 待完成
1. **`use fun` / `public use fun`**
   - 将 `UseStmt` 与 `PublicUseFun` 的语法整合进解析树。
   - 增加 PSI 节点与 stub/索引（若需要）。
   - 参考文件：
     - `src/main/grammars/MoveParser.bnf`
     - `src/main/kotlin/org/sui/lang/core/psi/*`
     - `src/main/kotlin/org/sui/lang/core/stubs/*`
2. **`macro fun` 语义信息可访问**
   - 已有 `macro` 修饰与 `MvFunction.isMacro`，需确认 PSI 结构与索引可用。
3. **`type` 关键字处理**
   - 作为关键字保留，仅允许 `` `type` `` 作标识符。
   - 若确认 Move 2024 存在 `type` 声明语法，再建 PSI/stub。

### 验收标准
- `use fun` 在 PSI 树中可被遍历，并能通过引用解析定位目标函数。
- `macro fun` 在 PSI/索引层可被稳定识别。

## P2：解析/推断/诊断完善（核心阶段）

### 目标
- 语义解析与诊断与 Move 2024 行为一致。

### 待完成
1. **`use fun` 语义解析**
   - 解析 alias、可见性、作用域。
   - 参与补全与自动导入。
2. **`let mut` 强制规则**
   - 对赋值/可变借用进行语义诊断。
3. **`public struct` 强制规则**
   - Move 2024 edition 下要求 struct 显式可见性（或 `public struct`）。
4. **`friend` 弃用与 `public(package)`**
   - Move 2024 禁止 `friend` / `public(friend)`；Move 1 保持兼容。
5. **索引语法 `[]` 规则完善**
   - 支持 `#[syntax(index)]` 的自定义索引函数解析与诊断。
6. **宏函数调用解析**
   - 解析 `macro fun` 作为可调用项。
   - 方法式宏调用 `x.macro!()` 支持与推断。
7. **enum/match 诊断**
   - match guard 类型检查。
   - enum 分支穷尽性/重复分支诊断（最小可用）。

### 验收标准
- 关键迁移点（let mut / public struct / friend）在 Move 2024 下有明确诊断；Move 1 下不报错。
- `use fun` 可影响解析/补全/跳转。

## P3：体验完善与测试覆盖

### 目标
- 提升开发体验与稳定性。

### 待完成
1. **补全与高亮**
   - `use fun`、宏函数、`type` 关键字等补全与高亮。
2. **格式化规则**
   - `use fun`、`macro fun`、`match` 语法格式化。
3. **测试**
   - 添加 PSI/解析/诊断/推断测试用例。
   - 覆盖 Move 2024 迁移关键路径。
4. **迁移辅助（可选）**
   - 若需要，可加入 `sui move migrate` 的 IDE 入口或提示文档。

### 验收标准
- 迁移指南示例在 IDE 内无解析/语义错误。
- 关键功能有最小测试覆盖（至少 1-2 条/特性）。

## 风险与决策点

- `type` 是否为实际类型别名语法：需以规范为准，避免过度实现。
- namespace/path 规则变化是否影响现有解析：需对 Move Book 与 Sui 实现再对齐。
- `enum/match` 的穷尽性诊断范围：建议先做最小可用，避免复杂性扩散。

## 建议优先级

1. P0 补齐 `2024.beta` + 统一 gate。
2. P1 `use fun` PSI 与解析落地。
3. P2 let mut / public struct / friend 禁用诊断。
4. P2 索引语法 `#[syntax(index)]` 规则。
5. P3 体验与测试。
