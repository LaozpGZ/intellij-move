## 背景

语法支持 nested use，但深层组合与 IDE 行为（resolve/optimize imports/completion）缺系统性回归；std 默认导入场景也需锁定。

## 目标

建立 nested use + std default-use 的可回归测试矩阵。

## 任务

- [ ] 补齐多层 nested use 样例（含 alias / Self 混合）
- [ ] 验证 resolve 与 optimize imports 一致性
- [ ] 验证 completion 在该场景下稳定
- [ ] 增加 project 级回归样例

## DoD

- [ ] nested use 深层样例全部通过
- [ ] optimize imports 不破坏语义
- [ ] completion 与 resolve 一致
- [ ] 定向回归与全量测试通过

## 建议测试

- `Resolve*`（imports/use 相关）
- `OptimizeImports*`
- completion 相关测试
