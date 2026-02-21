## 背景

Move 2024 的 equality 支持 automatic referencing。当前 value/ref 组合缺显式保障。

## 目标

补齐 `==/!=` 在 value/ref 组合下的推断兼容规则，并固化回归测试。

## 任务

- [ ] 明确 `T` vs `&T`、`&T` vs `T` 的兼容策略
- [ ] 修正 equality 推断逻辑（必要时）
- [ ] 增加正反向测试

## DoD

- [ ] `x == &x` / `&x == x` 行为符合预期
- [ ] 不兼容类型保持稳定报错
- [ ] 不破坏既有 equality 用例
- [ ] 定向回归与全量测试通过

## 建议测试

- `ExpressionTypesTest`
- `MvTypeCheckInspectionTest`
