## 背景

Move 2024 支持 positional fields（例如 `struct S(u64, bool)`）。当前字段抽象主链路仍偏 named fields，存在语义不一致风险。

## 目标

补齐 positional fields 在 parser/resolve/types/inspection 的端到端能力。

## 任务

- [x] 统一 fields 抽象入口，覆盖 tuple fields
- [x] 补齐 positional field 访问解析与错误提示
- [x] 补齐类型推断与类型检查
- [x] 增加 parser/resolve/types/inspection 回归测试

## DoD

- [x] `struct S(u64, bool)` 的声明、构造、解构、访问链路全部可用
- [x] 非法 positional field 访问有稳定报错
- [x] 定向回归与全量测试通过

## 建议测试

- `ResolveStructFieldsTest`
- `ExpressionTypesTest`
- `MvTypeCheckInspectionTest`
