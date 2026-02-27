# Move 2024 支持落地计划（Plan v2）

> Last updated: 2026-02-06
> Scope: IntelliJ Move Plugin（Sui/Aptos）
> Goal: 以最小风险补齐 Move 2024 的“必要闭环能力”，优先保证诊断正确性与回归稳定性。

## 0. 当前基线

### 已完成（本轮之前）
- `use fun` 在 AutoImport / Optimize Imports 主链路已接入。
- 宏语义已收敛到统一入口（`MacroSemanticService`），并完成一轮回归。

### 仍需补齐的必要能力（按优先级）
1. Move 2024 特性开关未完全落地（`macroFunctions` / `typeKeyword`）。
2. `match` 诊断能力不足（缺少穷尽性/重复分支诊断）。
3. `type` 语义未形成完整闭环（token 已有，声明级语义未完成）。
4. Move 2024 关键规则测试覆盖不足（`public struct` / `let mut`）。
5. 方法式宏调用（`x.macro!()`）缺少回归测试。
6. 导入体系仍有历史 TODO 测试债。

---

## 1. 设计原则

- **KISS**：先做最小可用闭环，不做宏展开引擎。
- **YAGNI**：不提前实现不在本轮验收范围内的复杂语义。
- **DRY**：统一语义入口与诊断入口，避免并行硬编码分支。
- **SOLID**：
  - S：解析/推断/诊断/补全职责分离；
  - O：优先以 feature gate 扩展，减少侵入式变更；
  - D：上层依赖语义服务而非底层注册表细节。

---

## 2. 分阶段执行计划

## P0（高优先级）：特性开关闭环（先落地）

### 目标
将 `MoveLanguageFeatures` 中已定义但未被实际消费的特性闭环到解析与补全路径。

### 具体任务
1. `macroFunctions` gate 落地到 parser util：
   - 文件：`src/main/kotlin/org/sui/lang/core/MoveParserUtil.kt`
   - 点位：`macroKeyword(...)`
   - 行为：当 `macroFunctions=false` 时，不将 `macro` 视为上下文关键字（保持为普通标识符语义）。

2. `typeKeyword` gate 落地到关键词补全：
   - 文件：`src/main/kotlin/org/sui/lang/core/completion/KeywordCompletionContributor.kt`
   - 行为：模块级关键字补全中，`type` 仅在 `features.typeKeyword=true` 时提供。

### 验收标准
- Move 1 配置下：
  - `macro` 不应触发 `macro fun` 语法路径。
  - 关键词补全不出现 `type`。
- Move 2024 配置下：行为与现有一致。

---

## P1（高优先级）：规则测试补齐（public struct / let mut）

### 目标
为已实现规则补上缺失的自动化保障，防止回归。

### 具体任务
1. 新增 `public struct` 规则测试：
   - 目标：Move 2024 下非 `public struct` 报错；Move 1 下不报错。
   - 主要文件：`src/test/kotlin/org/sui/ide/annotator/syntaxErrors/compilerV2/*`

2. 新增 `let mut` 规则测试：
   - 目标：Move 2024 下赋值与 `&mut` 借用检查生效；Move 1 下兼容旧行为。
   - 主要文件：`src/test/kotlin/org/sui/ide/annotator/errors/*`

### 验收标准
- 两类规则均有正向/反向测试。
- 错误信息与 `Diagnostic.kt` 文案一致。

---

## P2（中优先级）：方法式宏调用回归补齐

### 目标
补齐 `x.macro!()` 路径的解析/推断回归测试，锁住现有能力。

### 具体任务
- 新增最小测试：
  - 能解析并推断到 `macro fun`；
  - 非宏函数不应被 `!` 调用通过；
  - unresolved 场景报错合理。
- 主要文件：
  - `src/test/kotlin/org/sui/lang/types/*`
  - `src/test/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspectionTest.kt`

### 验收标准
- 至少覆盖 1 条成功 + 1 条失败路径。

---

## P3（中优先级）：导入体系测试债清理

### 目标
清理明确的 TODO 测试空洞，提升导入相关变更的回归信心。

### 具体任务
- 补齐以下文件中的 TODO 测试位：
  - `src/test/kotlin/org/sui/ide/inspections/imports/ImportCandidatesTest.kt`
  - `src/test/kotlin/org/sui/ide/inspections/MvUnusedImportInspectionTest.kt`

### 验收标准
- 相关 TODO 被替换为可执行测试或删除无效 TODO。

---

## P4（后续阶段）：`match` 诊断最小可用版

### 目标
在不引入复杂控制流分析的前提下，先交付高价值 `match` 诊断。

### 建议最小范围（MVP）
1. **重复分支诊断**（先做，复杂度低、收益高）
   - 同一枚举 variant 重复出现时报错。
2. **穷尽性初版诊断**（可选）
   - 当匹配目标是已知 enum 且无 `_` 分支时，提示缺失 variants。

### 暂不纳入本轮
- 复杂 guard-aware 穷尽性。
- 跨模块复杂模式组合覆盖证明。

---

## 3. `type` 语义闭环路线（设计占位）

> 本节作为后续设计，不阻塞当前 P0/P1/P2/P3。

### 现状
- lexer/parser token 层已有 `TYPE_KW`，但无完整声明级语义（alias PSI/stub/index/resolve）。

### 后续实施步骤（分拆建议）
1. BNF 增加 `type` 声明规则（如规范确认支持）。
2. 增加对应 PSI 节点与 ext 接口。
3. 按需接入 stub/index（仅在需要全局检索时）。
4. 解析/推断中接入 alias 展开或引用解析。
5. 补齐 parser/resolve/type/inspection 测试。

---

## 4. 测试与回归策略

### 定向回归（每阶段执行）
- `./gradlew test --tests "*MoveToml*" --tests "*Keyword*Completion*"`
- `./gradlew test --tests "*SyntaxError*" --tests "*ValueArgumentsNumberErrorTest"`
- `./gradlew test --tests "*UnresolvedReferenceInspectionTest" --tests "*OptimizeImportsTest" --tests "*AutoImportFixTest"`

### 阶段完成后
- 至少一次 `./gradlew test` 全量回归。

---

## 5. 风险与回滚

- 风险：feature gate 影响 parser 行为导致历史 fixture 波动。  
  应对：限定修改点（仅 `macroKeyword` / completion `type` 候选），失败即按文件粒度回滚。

- 风险：新增诊断测试与现有文案不一致。  
  应对：统一引用 `Diagnostic.kt` 当前文案，不自造提示语。

---

## 6. 完成定义（DoD）

当满足以下条件，可判定本轮完成：
- P0 与 P1 全部完成并通过定向回归。
- P2 至少完成最小回归（1 成功 + 1 失败）。
- P3 清理现有 TODO 测试位。
- 关键变更具备可点击文件定位与测试命令结果记录。
