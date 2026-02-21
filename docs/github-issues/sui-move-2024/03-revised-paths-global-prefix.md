## 背景

Move 2024 路径风格含全局 `::` 前缀。当前解析起始规则缺直接支持。

## 目标

补齐全局路径在 parser/resolve/completion 的一致行为。

## 任务

- [ ] 扩展语法支持 `::...` 起手路径
- [ ] 补齐 resolve 与错误恢复
- [ ] 校准 completion 和跳转行为
- [ ] 增加 parser/resolve/completion 回归测试

## DoD

- [ ] `::std::vector`、`::sui::...` 可解析
- [ ] 跳转目标正确
- [ ] 补全结果稳定
- [ ] 定向回归与全量测试通过

## 建议测试

- `ResolveModulesTest`
- `ModulesCompletionTest`
- parser 快照测试
