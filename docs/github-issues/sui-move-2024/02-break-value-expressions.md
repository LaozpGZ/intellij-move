## 背景

Move 2024 引入 break expressions。当前 `break <expr>` 支持不完整。

## 目标

支持 `break value`（含 label 场景）并完成类型汇合规则。

## 任务

- [x] 扩展语法支持 `break <expr>`
- [x] 推断层实现 break value 的类型汇合
- [x] 补齐 label + break value 的诊断
- [x] 增加 parser/types/annotator 回归测试

## DoD

- [x] `loop { break 1; }` 可解析且类型正确
- [x] 分支 break value 类型冲突有稳定报错
- [x] 与旧 `break` 行为兼容
- [x] 定向回归与全量测试通过

## 建议测试

- `BreakExprTest`
- `ExpressionTypesTest`
- `MvTypeCheckInspectionTest`
- `CompleteParsingTest`
