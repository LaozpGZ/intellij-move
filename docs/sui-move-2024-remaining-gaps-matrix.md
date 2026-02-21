# Sui Move 2024 剩余缺口清单 + 测试矩阵

> 更新时间：2026-02-07  
> 范围限定：**仅 Sui Move**（不含 Aptos 运行配置/生态能力）

## 1. 基线来源（对照标准）

- Sui 官方迁移页：<https://docs.sui.io/guides/developer/advanced/move-2024-migration>
- Move Book 迁移页：<https://move-book.com/guides/2024-migration-guide/>

Sui 官方页列出的 Move 2024 关键迁移点包含：新关键字、方法语法、索引语法、`public(package)`、位置字段（positional fields）、`enum/match`、nested `use`、自动引用比较、循环标签与 `break` 表达式、路径与命名空间调整。

---

## 2. 当前已闭环（Sui 主线）

以下能力已在仓库中可见，不列入“剩余缺口”：

1. `type` 声明级闭环（语法入口 + item 接入）
   - `src/main/grammars/MoveParser.bnf:275`
   - `src/main/grammars/MoveParser.bnf:285`
   - `src/main/grammars/MoveParser.bnf:437`
2. `match` 诊断从重复分支扩展到不可达/穷尽性最小版
   - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:234`
   - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:249`
   - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:304`
   - `src/test/kotlin/org/sui/ide/annotator/errors/MatchArmDuplicateErrorTest.kt:39`
3. 新 feature override 在配置页可见并可持久化
   - `src/main/kotlin/org/sui/cli/settings/PerProjectSuiConfigurable.kt`
4. 宏补全受 `macroFunctions` gate 约束
   - `src/main/kotlin/org/sui/lang/core/completion/providers/MacrosCompletionProvider.kt:51`
5. 全量 `test` + `verifyPlugin` 已具备可运行坐标（IU-253.31033.19）
   - `build.gradle.kts:170`
   - `gradle-253.properties:19`

---

## 3. 仍需补齐的 Sui 剩余缺口

> 目标口径：可实用 + 新特性可靠支持（发布质量）。

### P1（强烈建议发布前完成）

### GAP-SUI-01：位置字段（positional fields）语义链未完整打通

- 现状证据：字段聚合仍只返回 named fields。
  - `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt:24`
  - `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt:25`
- 风险：`struct S(u64, bool)` 的字段访问/解析/类型检查可能出现能力缺口或不一致。

### GAP-SUI-02：`break` 表达式仍停留在旧语法（缺 value）

- 现状证据：语法仅支持 `break` 或 `break 'label`，未支持 value。
  - `src/main/grammars/MoveParser.bnf:1102`
- 现状证据：循环推断按 `TyNever`/`TyUnit` 路径处理，未体现 break-value 汇合。
  - `src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt:2243`
  - `src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt:2336`
- 风险：与 Move 2024 的“break expressions”能力不一致。

### GAP-SUI-03：路径/命名空间更新（global `::` 风格）缺直接支持

- 现状证据：`PathImpl` 起始规则不接受全局前缀 `::` 起手。
  - `src/main/grammars/MoveParser.bnf:1301`
  - `src/main/grammars/MoveParser.bnf:1304`
- 风险：Move 2024 新路径风格在解析/补全/resolve 上可能不完整。

### GAP-SUI-04：`match` 诊断仍非 guard-aware 完整语义

- 现状证据：guard 分支在 variant 抽取时直接跳过。
  - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:211`
- 现状证据：穷尽性判定基于无 guard 的 catch-all/covered 统计。
  - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:306`
  - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:310`
- 风险：复杂 guard 场景下可能出现“过保守”或“过乐观”判定。

### P2（建议尽快补齐，属于稳定性与门禁质量）

### GAP-SUI-05：自动引用比较（automatic referencing in equality）缺显式保障

- 现状证据：类型合并仅覆盖 `ref-ref`，未见 value/ref 自动借用策略。
  - `src/main/kotlin/org/sui/lang/core/types/infer/InferenceContext.kt:505`
- 风险：`x == &x` / `&x == x` 等 Move 2024 期望行为可能不稳定。

### GAP-SUI-06：nested `use` 深层组合 + std 默认导入场景缺回归锁定

- 现状证据：语法支持递归 use group，但缺少深层回归样例覆盖。
  - `src/main/grammars/MoveParser.bnf:665`
  - `src/main/grammars/MoveParser.bnf:667`
- 风险：语法支持与 IDE 行为（resolve/optimize import/completion）可能失配。

### GAP-SUI-07：历史 TODO 测试债仍在

- `src/test/kotlin/org/sui/ide/annotator/syntaxErrors/AllowedSpecStatementsTest.kt:3`
- `src/test/kotlin/org/sui/lang/types/AcquiresTypesTest.kt:255`

### GAP-SUI-08：`verifyPlugin` 通过依赖 failureLevel 放宽（可用但不够“硬”）

- 现状证据：容忍 `INTERNAL_API_USAGES`。
  - `build.gradle.kts:185`
  - `build.gradle.kts:192`
- 风险：短期可过门禁，长期升级时存在潜在兼容性波动。

---

## 4. 对应测试矩阵（Sui 专项）

| Gap ID | 优先级 | 变更层级 | 最小新增测试场景 | 建议落点 |
|---|---|---|---|---|
| GAP-SUI-01 positional fields | P1 | parser + resolve + types + inspection | `struct S(u64, bool)` 的声明、构造、解构、字段访问（含非法索引） | `src/test/kotlin/org/sui/lang/parser/`、`src/test/kotlin/org/sui/lang/resolve/ResolveStructFieldsTest.kt`、`src/test/kotlin/org/sui/lang/types/ExpressionTypesTest.kt`、`src/test/kotlin/org/sui/ide/inspections/MvTypeCheckInspectionTest.kt` |
| GAP-SUI-02 break value | P1 | parser + types + annotator | `loop { break 1; }`、带 label 的 break value、类型不一致分支报错 | `src/test/kotlin/org/sui/lang/parser/`、`src/test/kotlin/org/sui/lang/types/BreakExprTest.kt`、`src/test/kotlin/org/sui/ide/annotator/errors/` |
| GAP-SUI-03 revised paths | P1 | parser + resolve + completion | `::std::vector`、`::sui::...` 路径解析、补全和跳转 | `src/test/kotlin/org/sui/lang/parser/`、`src/test/kotlin/org/sui/lang/resolve/ResolveModulesTest.kt`、`src/test/kotlin/org/sui/lang/completion/names/ModulesCompletionTest.kt` |
| GAP-SUI-04 match guard-aware | P1 | annotator + types | guard 参与覆盖判定：不可达与穷尽性在 guard 下的边界行为 | `src/test/kotlin/org/sui/ide/annotator/errors/MatchArmDuplicateErrorTest.kt`（扩展） |
| GAP-SUI-05 auto-referencing equality | P2 | types + inspection | `T` vs `&T` 在 `==/!=` 的兼容与反例（不兼容类型仍报错） | `src/test/kotlin/org/sui/lang/types/ExpressionTypesTest.kt`、`src/test/kotlin/org/sui/ide/inspections/MvTypeCheckInspectionTest.kt` |
| GAP-SUI-06 nested use + std default use | P2 | parser + resolve + optimize imports + completion | 深层 `use` 组合（多层 `{}`）+ 未显式 `use` 时 std 常用项行为一致性 | `src/test/kotlin/org/sui/lang/resolve/`、`src/test/kotlin/org/sui/ide/refactoring/optimizeImports/`、`src/test/kotlin/org/sui/lang/completion/` |
| GAP-SUI-07 TODO 测试债 | P2 | test infra | 将 TODO 样例转为可执行断言并纳入全量回归 | `AllowedSpecStatementsTest.kt`、`AcquiresTypesTest.kt` |
| GAP-SUI-08 verifier 严格化 | P2 | build gate | 保留当前坐标，逐步减少容忍级别并监控报告零增量 | `build.gradle.kts` + `./gradlew verifyPlugin` 报告审查 |

---

## 5. 建议执行顺序（仅 Sui）

1. GAP-SUI-01（positional fields）
2. GAP-SUI-02（break value）
3. GAP-SUI-03（revised paths）
4. GAP-SUI-04（match guard-aware）
5. GAP-SUI-05~08（稳定性与门禁）

---

## 6. 门禁命令（建议）

```bash
./gradlew test --no-daemon
./gradlew verifyPlugin --no-daemon
```

针对单项改动可先跑定向：

```bash
./gradlew test --tests "org.sui.ide.annotator.errors.MatchArmDuplicateErrorTest" --no-daemon
./gradlew test --tests "org.sui.lang.types.BreakExprTest" --no-daemon
./gradlew test --tests "org.sui.lang.resolve.ResolveStructFieldsTest" --no-daemon
```

