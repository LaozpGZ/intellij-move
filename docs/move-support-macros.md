# 宏语义统一收敛（Phase-1，低风险高收益）

  ## 摘要

  目标是把“宏相关的多条语义链路”收敛到一个统一入口，先解决你指出的最高耦合点：类型推断硬编码漂移、参数诊
  断分叉、补全来源分裂。
  本阶段不做真正的宏展开引擎，只做“语义单一事实源（SSOT）”重构，确保行为等价、可回归、可继续演进。

  ## 范围与边界

  - In scope
      - 统一 MacroSpec 消费方式（infer / annotator / completion / unresolved）
      - 剥离 TypeInferenceWalker 内建宏硬编码分支到可复用语义层
      - 补齐宏回归测试矩阵（参数个数、返回类型、补全、未解析误报）
  - Out of scope
      - 不改 parser 语法行为（MoveParserUtil.kt 仅保持兼容）
      - 不改 PSI 失效策略（MvPsiManager.kt 暂不动）
      - 不做宏展开/展开缓存/展开后 AST

  ## 接口与类型变更（决策已定）

  1. 新增 src/main/kotlin/org/sui/lang/core/macros/MacroSemanticService.kt
      - interface MacroSemanticService
          - fun specOf(name: String): MacroSpec?
          - fun isBuiltin(name: String): Boolean
          - fun completionSpecs(includeStdlib: Boolean): List<MacroSpec>
          - fun inferReturnType(call: MvMacroCallExpr, ctx: TypeInferenceWalker): Ty?
          - fun expectedArgsCount(call: MvMacroCallExpr): Int?
  2. 新增 DefaultMacroSemanticService（实现上述接口）
      - 内部组合当前 MvMacroRegistry + 现有内建宏推断逻辑
  3. MvMacroRegistry 保留为数据提供层，不再承载业务分支
      - 可选拆分文件（建议）：
          - BuiltinMacroSpecs.kt
          - StdlibMacroSpecs.kt
  4. TypeInferenceWalker、MvErrorAnnotator、MacrosCompletionProvider、MvUnresolvedReferenceInspection 改
     为依赖 MacroSemanticService 读取宏语义

  ## 实施步骤（按 PR 切分，可直接执行）

  ### PR-1：语义入口落地（不改行为）

  - 新建 MacroSemanticService 与默认实现。
  - 将 MvMacroRegistry.specOf/isBuiltin/completionSpecs 调用迁移到 service。
  - MvMacroRegistry 仅保留 MacroSpec 数据与轻量查询。
  - 验收：现有测试全绿，行为不变（纯重构）。

  ### PR-2：类型推断收敛（移除硬编码散点）

  - 在 TypeInferenceWalker.kt 中：
      - 保留 resolveMacroFunction(...)（用户 macro fun 分支）。
      - 将 inferBuiltinMacroCallExprTy(...) 主分支迁移为 macroSemanticService.inferReturnType(...)。
      - vector!/option!/result!/bcs! 具体推断逻辑下沉到 service 实现（同文件私有 helper 可暂留，后续再
        抽）。
  - 验收：OptionResultMacroTypeTest 行为一致；新增内建宏类型断言通过。

  ### PR-3：诊断与补全收敛（单一事实源）

  - MvErrorAnnotator.expectedArgsCountForMacro(...)：
      - 首先走 macroSemanticService.expectedArgsCount(call)。
      - 回退函数签名逻辑保留（兼容用户 macro fun）。
  - MacrosCompletionProvider：
      - 宏候选统一来自 macroSemanticService.completionSpecs(includeStdlib) + 用户作用域宏合并去重。
  - MvUnresolvedReferenceInspection：
      - builtin 宏绕过改用 macroSemanticService.isBuiltin(name)。
  - 验收：参数错误、补全、未解析误报测试通过。

  ## 测试方案（必须新增/更新）

  1. src/test/kotlin/org/sui/lang/types/OptionResultMacroTypeTest.kt
      - 保留现有 option!/result!，补 bcs! 返回 vector<u8> 断言。
  2. src/test/kotlin/org/sui/ide/annotator/errors/ValueArgumentsNumberErrorTest.kt
      - 新增 result!、bcs! 参数个数错误用例。
  3. 新增 src/test/kotlin/org/sui/lang/core/completion/MacroCompletionProviderTest.kt（若已有同类测试类
     则并入）
      - 覆盖 builtin + stdlib 可见性差异、去重规则。
  4. src/test/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspectionTest.kt
      - 验证 builtin 宏不误报 unresolved，未知宏仍报错。
  5. 回归命令（定向）：
      - ./gradlew test --tests "org.sui.lang.types.OptionResultMacroTypeTest" --tests
        "org.sui.ide.annotator.errors.ValueArgumentsNumberErrorTest" --tests "*Macro*Completion*Test"
        --tests "*UnresolvedReferenceInspectionTest"

  ## 验收标准（完成定义）

  - TypeInferenceWalker 不再维护独立 builtin 宏大 when 语义分支（可保留极少量桥接代码）。
  - 参数个数规则在“诊断/补全/未解析”使用同一语义源，不出现重复硬编码。
  - 宏相关核心测试全绿，且无行为回退。
  - 代码复杂度下降：宏语义改动主要落在 macros/ 目录，调用方仅做委托。

  ## 风险与应对

  - 风险：service 抽象过早导致跳转层增多
    应对：Phase-1 仅一层接口 + 默认实现，不引入扩展框架。
  - 风险：用户宏与内建宏优先级变化
    应对：保持现有优先级（先用户解析，再 builtin 语义回退）。
  - 风险：补全候选顺序变化导致快照测试波动
    应对：显式固定排序（builtin 优先、再 stdlib、最后用户或按现有规则保持）。

  ## 假设与默认决策

  - 默认不调整 parser 与 PSI invalidation 路径（避免高风险联动）。
  - 默认维持当前宏关键字语义与名字集合不变。
  - 默认继续采用“语义模拟”路线，不引入展开 AST。
  - 默认以 3 个小 PR 渐进合并，确保每步可回滚、可定位。