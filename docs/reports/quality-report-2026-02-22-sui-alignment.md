# Sui Move 插件质量与 Sui 生态特性对齐报告（2026-02-22）

## 1. 先回答你的问题

有，`261` 我已补跑 `test`。  
本次同口径结果：

- `253 test`：`1807` tests，`0` failures，`0` errors，`0` skipped
- `261 test`：`1807` tests，`0` failures，`0` errors，`0` skipped

## 2. 本轮质量验证结果（含 253/261）

### 2.1 构建与测试

- `ORG_GRADLE_PROJECT_shortPlatformVersion=253 ./gradlew test --no-daemon`：通过（本次命中缓存，历史同日全量执行已通过）
- `ORG_GRADLE_PROJECT_shortPlatformVersion=261 ./gradlew test --no-daemon`：通过（本次实际执行，BUILD SUCCESSFUL in 1m 21s）
- 当前测试汇总：`build/test-results/test` 共 156 个 suite，合计 1807 用例，全绿

### 2.2 Plugin Verifier（双平台）

- `253`：Compatible，`scheduled=12`，`deprecated=16`，`experimental=119`
- `261`：Compatible，`scheduled=12`，`deprecated=17`，`experimental=119`
- 结论：261 相比 253 的 deprecated +1，兼容债务仍需持续清理

## 3. 对照基线（你指定的两侧）

- Sui docs guides：`https://docs.sui.io/guides.md`
- Sui 源码仓库：`https://github.com/MystenLabs/sui`

本地插件能力证据（核心）：
- `src/main/resources/META-INF/plugin.xml:103`
- `src/main/resources/META-INF/plugin.xml:170`
- `src/main/resources/META-INF/plugin.xml:251`
- `src/main/resources/META-INF/plugin.xml:478`
- `src/main/resources/META-INF/plugin.xml:507`
- `src/main/kotlin/org/sui/cli/MovePackage.kt:21`
- `src/main/kotlin/org/sui/toml/MoveTomlErrorAnnotator.kt:61`
- `src/main/kotlin/org/sui/lang/utils/Diagnostic.kt:213`

## 4. “Guides ↔ 源码 ↔ 插件”一一对应矩阵

| Guides 分类 | Sui 官方/源码侧能力 | 插件对应状态 | 证据与说明 |
|---|---|---|---|
| Get started（安装/客户端/地址/水龙头/Hello） | `guides.md` 中 1-6 步；源码含 `sui-faucet`、CLI 等 | **高对应** | 插件有下载 CLI、配置、地址/环境/对象操作与 Get Gas：`plugin.xml:483`、`plugin.xml:507`、`plugin.xml:512` |
| Objects | docs 有 object model/ownership/dynamic fields | **部分对应** | 插件提供 Move 语义解析与诊断，但不提供链上对象模型可视化分析引擎 |
| Packages | docs 有包管理/升级/策略；源码有 `sui-package-*` | **高对应** | `Move.toml` edition/地址校验与包特性门控：`MoveTomlErrorAnnotator.kt:61`、`MovePackage.kt:47` |
| Transactions | docs 有签名/PTB/赞助交易；源码有 `sui-transaction-*`、RPC | **中高对应** | 插件支持运行配置、行内运行入口和 CLI 工作流：`plugin.xml:84`、`plugin.xml:100` |
| Accessing data（gRPC/GraphQL/Indexer） | docs 明确列出；源码有 `sui-json-rpc`、`sui-indexer-*`、`sui-kv-rpc` | **低对应** | 插件暂无 GraphQL/gRPC/indexer 专项能力（查询构造、调试、schema 导航等） |
| Currencies & Tokens | docs 有 coin/token/vesting；源码有 framework/coin 模块 | **部分对应** | 仅通过通用 Move 语言能力间接支持，缺少币种模板与专项检查 |
| NFTs | docs 有 NFT/soulbound/rental；源码含 `kiosk/` 与相关模块 | **部分对应** | 通用语言支持可覆盖代码编辑，缺少 NFT/Kiosk 专项工具链 |
| On-chain primitives（time/random） | docs 明确列出 | **部分对应** | 可写可检 Move 代码；无运行时语义模拟与策略审计插件功能 |
| Cryptography（签名、多签、zkLogin） | docs 明确列出；源码含 `shared-crypto`、`sdk/zksend` 等 | **低对应** | 无 zkLogin/多签/签名流程向导或调试面板，主要靠通用语言支持 |
| Nautilus | docs 有 Nautilus 专章 | **不对应** | 插件无 Nautilus 专项集成 |
| Example applications | docs 有 E2E 示例（oracle/game 等） | **低对应** | 无示例工程向导/模板注入，仅基础文件与语言能力 |
| Operator guides（全节点/验证者/桥） | docs 有 operator 专章；源码有 `sui-node`、`bridge/` | **不对应** | 插件定位 IDE 开发，不覆盖节点运维 |
| SuiPlay0X1 | docs 有专章与钱包集成 | **不对应** | 插件未提供 SuiPlay0X1 相关支持 |

## 5. 差距统计（按分类）

- 高对应：3（Get started、Packages、Transactions）
- 部分对应：5（Objects、Currencies/Tokens、NFTs、On-chain primitives、部分示例开发）
- 低/不对应：5（Accessing data、Cryptography、Nautilus、Operator、SuiPlay0X1）

结论：插件当前是“**Sui Move 开发内核很强**，但**生态外围能力覆盖有限**”。

## 6. 建议路线（按优先级）

### P0（近期）

1. 继续压降 verifier 债务（特别是 deprecated/scheduled API）。
2. 维持 `253 + 261` 双轨门禁，避免 261 漂移。

### P1（对齐 docs 的最小闭环）

1. 增加 Accessing Data 基础支持：Sui JSON-RPC/GraphQL 请求模板、常用查询补全、结果面板。
2. 增加 PTB/交易脚手架模板（与现有 run configuration 结合）。

### P2（生态深水区）

1. 增加 NFT/Kiosk/Coin 领域模板与检查规则。
2. 评估 zkLogin/多签流程辅助（文档跳转 + 配置校验 + 示例生成）。

## 7. 关键外部依据

- Sui Guides 总览：`https://docs.sui.io/guides.md`
- Sui Guides 源文件：`https://raw.githubusercontent.com/MystenLabs/sui/main/docs/content/guides.mdx`
- Sui 仓库根目录：`https://github.com/MystenLabs/sui`
- Sui 仓库内容 API：`https://api.github.com/repos/MystenLabs/sui/contents`
