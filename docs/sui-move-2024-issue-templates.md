# Sui Move 2024 剩余缺口 - GitHub Issue 模板（8条，含 DoD）

> 范围：仅 Sui Move  
> 用法：每个模板可直接复制到 GitHub Issue（标题建议已给出）

---

## 1) GAP-SUI-01：Positional Fields 语义闭环

**建议标题**：`feat(sui-move): close positional fields semantic loop`

```md
## 背景

Move 2024 支持 positional fields（如 `struct S(u64, bool)`）。当前实现中字段聚合主要走 named fields，positional fields 在解析/解析引用/类型检查链路存在潜在不一致。

## 目标

补齐 positional fields 在 parser/resolve/types/inspection 的端到端能力，达到“可实用 + 可回归”。

## 影响范围

- parser/PSI（tuple fields 相关）
- 字段解析（字段访问与错误提示）
- 类型推断与类型检查
- 相关 IDE inspection

## 任务

- [ ] 梳理 positional fields 在 PSI 与字段抽象中的统一访问入口
- [ ] 补齐 tuple field 访问解析（含合法/非法索引）
- [ ] 补齐类型检查与报错文案
- [ ] 增加 parser + resolve + types + inspection 回归测试

## DoD

- [ ] `struct S(u64, bool)` 场景下：声明、构造、解构、访问链路全部可用
- [ ] 非法 positional field 访问有稳定错误提示
- [ ] 新增测试全部通过，且不引入现有用例回归
- [ ] 全量 `./gradlew test --no-daemon` 通过

## 建议测试

- parser：tuple fields 语法快照
- resolve：字段访问正确绑定
- types：索引字段类型正确
- inspection：非法访问报错断言

## 非目标

- [ ] 不在本任务中重构无关字段模型
```

---

## 2) GAP-SUI-02：Break Value Expressions 支持

**建议标题**：`feat(sui-move): support break value expressions for Move 2024`

```md
## 背景

Move 2024 引入 break expressions。当前语法与类型推断路径对 `break <expr>` 支持不完整。

## 目标

支持 `break value`（含 label 场景）并完成类型汇合规则，保证 parser + types + diagnostics 一致。

## 影响范围

- BNF：BreakExpr 语法
- 类型推断：loop/while/for 与 break value 汇合
- 诊断：不兼容类型分支报错

## 任务

- [ ] 扩展 BreakExpr 语法，支持 `break <expr>`
- [ ] 在推断中实现 break value 与循环结果类型汇合
- [ ] 补齐 label + break value 的行为与报错
- [ ] 增加 parser/types/annotator 测试

## DoD

- [ ] `loop { break 1; }` 语法可解析且类型正确
- [ ] 多分支 break value 类型不兼容时有稳定报错
- [ ] label 场景行为正确
- [ ] 定向 + 全量测试通过

## 建议测试

- 正向：`break 1`、`break true`、label break
- 反向：分支类型冲突
- 边界：无 value 的 break 兼容旧行为

## 非目标

- [ ] 不扩展为复杂控制流优化
```

---

## 3) GAP-SUI-03：Revised Paths（全局 `::` 风格）

**建议标题**：`feat(sui-move): add revised path style support for global ::`

```md
## 背景

Move 2024 路径语法有更新（含全局 `::` 风格）。当前路径起始规则对该风格缺直接支持。

## 目标

补齐全局路径风格在 parser/resolve/completion 的一致行为。

## 影响范围

- parser：Path 起始规则
- resolve：全局路径解析
- completion/navigation：补全、跳转一致性

## 任务

- [ ] 扩展路径语法支持 `::...` 起手
- [ ] 补齐 resolve 逻辑与错误恢复
- [ ] 校准 completion/nav 行为
- [ ] 增加 parser/resolve/completion 回归测试

## DoD

- [ ] `::std::vector`、`::sui::...` 可解析
- [ ] 引用可跳转到正确目标
- [ ] 补全行为与非全局路径一致
- [ ] 全量测试通过

## 建议测试

- parser：全局路径语法快照
- resolve：跨模块定位
- completion：路径分段候选

## 非目标

- [ ] 不做无关命名空间重构
```

---

## 4) GAP-SUI-04：Match 诊断 guard-aware 增强

**建议标题**：`feat(annotator): make match diagnostics guard-aware`

```md
## 背景

当前 match 诊断已支持重复分支/不可达/穷尽性最小版，但 guard 场景仍是保守策略，存在边界误报/漏报风险。

## 目标

在不引入复杂控制流分析的前提下，增强 guard-aware 诊断准确性。

## 影响范围

- annotator：重复分支、不可达、穷尽性判定
- types（必要时）：guard 相关约束
- 测试：match 多场景覆盖

## 任务

- [ ] 明确 guard 分支在“覆盖性”中的计入规则
- [ ] 调整不可达与穷尽性判定
- [ ] 对关键边界场景补回归测试
- [ ] 校正文案与断言

## DoD

- [ ] guard 参与下的不可达判定可解释、可复现
- [ ] guard 参与下的穷尽性判定可解释、可复现
- [ ] 无新增明显误报（通过回归样例证明）
- [ ] 全量测试通过

## 建议测试

- `E::A if cond` 与 `E::A` 混合
- `_ if cond` 与 `_` 的相对顺序
- enum 全覆盖后再出现 guard/wildcard 的边界

## 非目标

- [ ] 不做完整控制流/SMT 级覆盖证明
```

---

## 5) GAP-SUI-05：Equality 自动引用（auto-referencing）

**建议标题**：`feat(types): align equality auto-referencing with Move 2024`

```md
## 背景

Move 2024 对 equality 支持自动引用。当前类型合并对 ref-ref 支持明确，但 value/ref 自动借用行为缺显式保障测试。

## 目标

补齐 `==`/`!=` 在 value/ref 组合下的类型兼容规则，并锁定回归。

## 影响范围

- type inference：equality 分支
- inspection：类型错误提示
- tests：类型与高亮

## 任务

- [ ] 明确 `T` vs `&T`、`&T` vs `T` 的兼容策略
- [ ] 实现或修正 equality 推断逻辑
- [ ] 增加正反向测试

## DoD

- [ ] `x == &x` / `&x == x` 行为符合预期
- [ ] 不兼容类型仍稳定报错
- [ ] 未破坏现有 equality 用例
- [ ] 全量测试通过

## 建议测试

- types：ExpressionTypes 中 equality 组合
- inspections：MvTypeCheckInspection 的错误断言

## 非目标

- [ ] 不扩展到比较运算符（`<`, `>`）语义变更
```

---

## 6) GAP-SUI-06：Nested Use + Std 默认导入一致性

**建议标题**：`test(resolve/imports): lock nested use and std default-use behavior`

```md
## 背景

语法支持 nested use，但深层组合与 IDE 行为（resolve/optimize import/completion）缺系统性回归。std 默认导入相关场景也需要锁定一致性。

## 目标

建立 nested use + std default-use 的可回归测试矩阵，保证语义与 IDE 行为一致。

## 影响范围

- parser（深层 use）
- resolve
- optimize imports
- completion

## 任务

- [ ] 补齐多层 nested use 样例（含 alias / Self 混合）
- [ ] 验证 resolve 与 optimize imports 行为
- [ ] 验证 completion 在该场景下无回退
- [ ] 增加 project 级回归样例

## DoD

- [ ] nested use 深层样例全部通过
- [ ] optimize imports 不破坏语义
- [ ] completion 与 resolve 结果一致
- [ ] 全量测试通过

## 建议测试

- `use 0x1::M::{A, B::{C, D}}` 类场景
- 有无显式 std use 的对照场景

## 非目标

- [ ] 不重写 import 排序策略
```

---

## 7) GAP-SUI-07：历史 TODO 测试债清理

**建议标题**：`test: convert legacy TODO tests into executable regressions`

```md
## 背景

仍有历史 TODO 测试空洞，影响“可靠支持”的信心与回归覆盖完整性。

## 目标

将 TODO 转为可执行测试或明确删除无效 TODO，减少测试债。

## 影响范围

- annotator syntax tests
- types tests

## 任务

- [ ] 处理 `AllowedSpecStatementsTest` 中注释 TODO
- [ ] 处理 `AcquiresTypesTest` 中注释 TODO
- [ ] 补齐最小正反向断言

## DoD

- [ ] 以上 TODO 不再以注释形式悬空
- [ ] 新测试可稳定通过
- [ ] 全量测试通过

## 建议测试

- AllowedSpecStatements：允许/不允许语句矩阵
- AcquiresTypes：递归/传递递归 acquires 类型路径

## 非目标

- [ ] 不做无关模块测试改造
```

---

## 8) GAP-SUI-08：VerifyPlugin 严格化与兼容性治理

**建议标题**：`chore(verification): harden verifyPlugin compatibility gate`

```md
## 背景

当前 verifyPlugin 已可运行，但依赖 failureLevel 容忍（含 INTERNAL_API_USAGES）。短期可用，长期升级风险仍在。

## 目标

在保持门禁可跑通前提下，逐步收紧容忍级别并建立可追踪治理节奏。

## 影响范围

- build.gradle.kts（pluginVerification 配置）
- plugin verifier 报告审查流程

## 任务

- [ ] 固化 verifier 目标 IDE 坐标与运行方式（installer）
- [ ] 建立 INTERNAL_API_USAGES 清单（来源类、调用点、替代方案）
- [ ] 分阶段下调容忍级别（按版本窗口）
- [ ] 在 CI/PR 中增加报告摘要

## DoD

- [ ] verifyPlugin 在指定坐标稳定通过
- [ ] INTERNAL_API_USAGES 有可追踪清单与 owner
- [ ] 本阶段定义的 failure level 收紧目标达成
- [ ] 文档化后续收敛计划

## 建议验证

- `./gradlew verifyPlugin --no-daemon`
- 检查 `build/reports/pluginVerifier/` 报告是否零新增

## 非目标

- [ ] 不要求一次性清零全部历史 internal API 使用
```

---

## 附：统一标签建议

可选标签：`sui-move`、`move-2024`、`parser`、`resolve`、`types`、`annotator`、`completion`、`tests`、`build`、`verification`

