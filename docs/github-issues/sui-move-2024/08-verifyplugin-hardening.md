## 背景

verifyPlugin 当前可运行，但依赖 failure level 放宽（含 INTERNAL_API_USAGES），长期兼容风险仍在。

## 目标

保持门禁可跑通前提下，逐步收紧容忍级别并建立可追踪治理。

## 任务

- [ ] 固化 verifier IDE 坐标与 installer 运行方式
- [ ] 建立 INTERNAL_API_USAGES 清单（来源/替代方案/owner）
- [ ] 规划分阶段收紧 failure level
- [ ] 在 CI/PR 中输出报告摘要

## DoD

- [ ] verifyPlugin 在目标坐标稳定通过
- [ ] INTERNAL_API_USAGES 有可追踪清单
- [ ] 本阶段收紧目标达成
- [ ] 治理计划文档化

## 建议验证

- `./gradlew verifyPlugin --no-daemon`
- 检查 `build/reports/pluginVerifier/` 报告零新增
