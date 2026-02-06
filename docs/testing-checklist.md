# Intellij Move 插件测试规范与清单

## 1. 目标与边界

- 目标：在合并前证明“需求已实现且未破坏既有能力”。
- 边界：无法数学上保证 100% 无缺陷，但可通过**需求-用例-结果**闭环，把漏测风险降到可接受范围。
- 原则：
  - KISS：只测当前改动需要验证的行为。
  - YAGNI：不为未来假设场景过度补测试。
  - DRY：复用现有测试基类与 fixture，避免重复搭建。
  - SOLID：按能力边界分层验证（解析、语义、IDE、CLI），单层失败可快速定位。

---

## 2. 强制门禁（DoD）

以下条目必须全部勾选后才允许合并：

- [ ] 每个需求或缺陷都有唯一条目（建议 `REQ-*`）。
- [ ] 每个 `REQ-*` 至少映射 1 个自动化测试（建议 `TEST-*`）。
- [ ] 每个新增语法/语义能力都覆盖：正向、反向、边界 3 类用例。
- [ ] 修改了报错文案或 quick fix 行为时，更新对应高亮/修复断言。
- [ ] 修改了补全、跳转、类型推断时，至少有 1 条回归测试覆盖旧缺陷路径。
- [ ] 不允许提交被注释/跳过的失败测试（`@Ignore`/临时跳过需附 issue 编号和恢复日期）。
- [ ] 本次改动涉及的测试全部通过。
- [ ] 至少执行 1 次完整 `./gradlew test` 并通过。

---

## 3. 按改动类型的最小测试集

> 命中任一改动类型，就必须执行该行“最小测试集”。若跨类型改动，测试集取并集。

| 改动类型 | 必测目录/能力 | 最小新增或更新要求 |
|---|---|---|
| 词法/语法（Lexer/Parser） | `src/test/kotlin/org/sui/lang/lexer`、`src/test/kotlin/org/sui/lang/parser` | 新语法至少 1 个 `.move` 输入 + 1 个 `.txt` 期望树快照 |
| 名称解析（Resolve） | `src/test/kotlin/org/sui/lang/resolve` | 至少覆盖 1 条“应解析”与 1 条“应未解析” |
| 类型推断（Types） | `src/test/kotlin/org/sui/lang/types` | 至少覆盖 1 条主路径 + 1 条错误或边界类型路径 |
| 代码补全（Completion） | `src/test/kotlin/org/sui/lang/completion`、`src/test/kotlin/org/sui/toml` | 至少覆盖“包含候选”与“不应出现候选” |
| 诊断/检查/快速修复（Annotator/Inspection/Fix） | `src/test/kotlin/org/sui/ide/annotator`、`src/test/kotlin/org/sui/ide/inspections` | 至少 1 条高亮断言 + 1 条 quick fix 应用后断言 |
| 格式化（Formatter） | `src/test/kotlin/org/sui/ide/formatter` + `src/test/resources/org/sui/ide/formatter.fixtures` | 必须提供 before/after 成对样例 |
| 重构/编辑器动作（Rename/Intentions/Typing） | `src/test/kotlin/org/sui/ide/refactoring`、`src/test/kotlin/org/sui/ide/intentions`、`src/test/kotlin/org/sui/ide/typing` | 至少覆盖光标位置关键路径（含 marker） |
| CLI/运行配置/外部系统 | `src/test/kotlin/org/sui/cli` | 至少 1 条命令参数或项目导入行为断言 |

---

## 4. 测试设计清单（写测试前）

- [ ] 明确本次改动影响层级：语法 / 语义 / IDE 交互 / CLI。
- [ ] 列出输入维度：合法输入、非法输入、最小输入、极端输入。
- [ ] 列出状态维度：编译器特性开关、命名地址、工程结构（单文件/多模块）。
- [ ] 明确可观察输出：PSI 树、resolve 目标、补全项、高亮、quick fix 后文本。
- [ ] 明确“不该发生”的行为（误报、误补全、错误跳转、错误格式化）。

---

## 5. 测试实现清单（写测试时）

- [ ] 优先复用现有基类与 fixture（如 `MvTestBase`、`MvParsingTestCase`、completion/annotation fixture）。
- [ ] 使用 marker 规范：`//^` 指向引用位置，`//X` 指向目标定义。
- [ ] 高亮断言使用 `<error descr="...">` / `<warning descr="...">`，避免模糊断言。
- [ ] quick fix 测试必须断言“应用前可见 + 应用后结果正确”。
- [ ] formatter 测试必须断言 before/after 实际不同，避免空测。
- [ ] 新测试命名与行为一致，文件名以 `*Test.kt` 结尾。

---

## 6. 执行清单（提交前）

### 6.1 定向回归（先快后全）

- [ ] 仅跑本次改动相关测试（按包或类过滤）。
- [ ] 定向失败已定位并修复，不带入无关改动。

示例：

```bash
./gradlew test --tests "org.sui.lang.parser.*"
./gradlew test --tests "org.sui.lang.resolve.*"
./gradlew test --tests "org.sui.ide.annotator.*"
```

### 6.2 全量回归

- [ ] 执行并通过：

```bash
./gradlew test
```

- [ ] 若涉及语法生成改动，再执行：

```bash
./gradlew generateLexer generateParser
./gradlew test
```

---

## 7. 覆盖率与“功能已实现”判定规则

当满足以下条件，可判定“该需求已实现并具备可回归性”：

- [ ] 需求清单中每一项都有对应测试且通过。
- [ ] 改动影响层级均有至少 1 条自动化断言。
- [ ] 主路径 + 失败路径 + 关键边界路径均被覆盖。
- [ ] 历史缺陷路径（若有）已固化为回归测试。
- [ ] 全量测试通过且无新增 flaky 现象。

> 建议：把本文件作为 PR 附件清单，逐项打勾并附命令输出摘要。

---

## 8. PR 可复制模板

```md
## 测试清单

- [ ] REQ 映射完成（REQ-*/TEST-*）
- [ ] 改动类型最小测试集完成
- [ ] 定向回归通过
- [ ] 全量 `./gradlew test` 通过
- [ ] 无 skip/ignore 的新增失败测试

## 已执行命令

1. `./gradlew test --tests "..."`
2. `./gradlew test`

## 结果

- 总结：
- 风险点：
- 后续观察项：
```
