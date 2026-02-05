# Move 2024 语法支持落地计划（Sui Move / IntelliJ 插件）

## 目标与范围
- **目标**：补齐 Move 2024 语法与语义支持，并确保解析/PSI/解析与类型推断/诊断/格式化/高亮/补全一致。
- **范围**：按 Move 2024 的核心增量特性逐条对照现有代码，给出可执行修复步骤。

## 现状与差距（逐条对照到当前代码）

### 0. Edition 识别与统一 Feature Gate（基础设施）
- **现状**
  - `Move.toml` 未解析 `edition` 字段：`src/main/kotlin/org/sui/cli/manifest/MoveToml.kt`。
  - 语法特性开关全部来自 `MvProjectSettingsService` 的手动配置：
    `src/main/kotlin/org/sui/cli/settings/MvProjectSettingsService.kt`。
- **缺口**
  - 无法根据 Move 2024/Move 1 自动启用/禁用特性。
  - 诊断与解析 gate 仅依赖 UI 设置，难以与项目级 `Move.toml` 对齐。
- **修复目标**
  - 解析 `edition` 并形成 `MoveEdition` + `MoveLanguageFeatures`，替代散落的手动开关。

### 1. 方法语法（receiver-style）+ `use fun` 导入
- **现状**
  - 解析与类型推断已经支持方法调用形式：
    - 解析：`MoveParser.bnf` 中 `DotExpr` / `MethodCall`。
    - 推断：`TypeInferenceWalker.kt` 里 `MvMethodCall` 分支。
    - Gate：`MvSyntaxErrorAnnotator` / `MvUnresolvedReferenceInspection` / `HighlightingAnnotator` 通过 `moveSettings.enableReceiverStyleFunctions` 控制。
  - `use fun` 只在 BNF 里存在占位定义，但未接入 `UseStmt`：
    - `MoveParser.bnf` 有 `UseFunFirst` / `PublicUseFun`，但 `UseStmt` 仅解析 `use UseSpeck`.
- **Move 2024 语法核对**
  - 方法语法是语法糖：首参为特殊 `self` 的函数可被视为方法，并以 `x.f(...)` 形式调用。
  - `use fun` 引入方法别名导入语法，可用于创建 public 或 internal 的方法别名（具体导出/作用域规则需以规范为准）。
  - 是否存在“对模块的 `use` 隐式生成 `use fun`”规则需进一步核对（暂不假设）。
- **缺口**
  - 不能解析/索引/解析 `use fun`，无法形成方法导入别名。
  - 方法语法 gate 与 edition 脱节。
- **修复目标**
  - 完整实现 `use fun` 语法与解析/解析路径/补全/重构支持。
  - receiver-style 由 Edition 自动开启（Move 2024 必开，Move 1 可报错或禁用）。

### 2. 索引语法（`v[i]`, `R[@addr]`）
- **现状**
  - 解析：`MoveParser.bnf` 有 `IndexExpr`。
  - 推断：`TypeInferenceWalker.kt` 中 `inferIndexExprTy` 已支持 vector 与资源索引推断。
  - Gate：`MvSyntaxErrorAnnotator` 与 `moveSettings.enableIndexExpr`。
- **Move 2024 语法核对**
  - 索引语法由函数属性 `#[syntax(index)]`（可选第二参数指定分隔符）声明。
- **缺口**
  - Edition 未接入 gate。
  - 诊断仍以“编译器 V2”作为概念，不对应 Move 2024 语义。
- **修复目标**
  - 以 `MoveEdition` 统一 gate，并补充索引语法错误提示与快速修复建议（可选）。

### 3. 宏函数（macro functions）与宏调用（`foo!()`）
- **现状**
  - 解析宏调用：`MacroCallExpr ::= PathImpl '!' ...`。
  - 内建宏注册与补全：`MvMacroRegistry` + `MacrosCompletionProvider`。
  - 推断：`TypeInferenceWalker` 对部分宏（`assert/debug/option/result/...`）做硬编码。
  - 语法层面存在 `macro` 修饰符：`MoveParserUtil.FunModifier.MACRO`。
- **Move 2024 语法核对**
  - 声明语法：`<visibility>? macro fun <identifier><[$type_parameters],*>([$identifier: type],*): <return_type> <body>`。
  - 约束：**宏函数的类型参数与形参名必须以 `$` 开头**；单独 `_` 允许，但不能作为前缀，`$_` 用于以下划线开头的宏参数。
  - 宏函数以 `!` 调用，存在方法式调用示例（如 `v.map!(...)`、`v.for_each!(...)`），并允许在“宏参数 lambda”内使用 `return` / `break` 等控制流。
- **缺口**
  - **宏定义（`macro fun`）未形成 PSI 语义**：没有 `MvFunction.isMacro` / stub 标记 / 索引。
  - **宏调用无法解析到用户定义宏**：`MacroCallExpr` 仅靠名称字符串。
  - **宏类型推断不基于宏函数签名**：无法利用 `macro fun` 的参数与返回类型。
  - **宏参数命名规则未校验**：目前 `IDENTIFIER` 允许 `$` 前缀，但未对“必须以 `$` 开头”的规则做诊断。
  - **方法式宏调用语法未覆盖**：当前 `DotExpr` 仅支持 `MethodCall` / `StructDotField`，不接受 `foo!` 形式。
- **修复目标**
  - 增加 `isMacro` PSI 属性与 stub 字段；解析 `macro fun` 进入索引。
  - 允许 `path!` 解析到 `macro fun`；在推断中优先使用宏函数签名。
  - 宏补全应包含当前作用域的宏函数。
  - 增加宏参数命名规则诊断（宏函数内 `$` 前缀与 `$_` 规则）。
  - 扩展语法以支持 `x.macro!()` 的方法式宏调用。

### 4. `enum` 与 `match`
- **现状**
  - `enum`/`match` 已在 BNF、PSI、推断、解析中存在：
    - `MoveParser.bnf`：`Enum`, `MatchExpr`, `Pat` 相关规则。
    - `TypeInferenceWalker.kt`：`inferMatchExprTy`。
    - 解析：`MvEnum` / `MvEnumVariant` stubs & `MvPath2ReferenceImpl` 的枚举变体解析。
  - 格式化与缩进已覆盖：`formatter/impl/indent.kt`、`spacing.kt`。
- **缺口**
  - 缺少 match 的诊断能力（如穷尽性/重复分支/guard 类型校验）。
  - 若 Move 2024 的 `match` 语法细节有变化（guard/模式），需要对齐。
- **修复目标**
  - 完善 match 诊断与测试覆盖（至少 guard 类型、enum variant 解析）。

### 5. `type` 关键字（保留字）
- **现状**
  - Lexer 里存在 `TYPE_KW`，但 **没有 `type` 声明语法**。
  - PSI / stub / 解析 / 索引均缺失。
- **Move 2024 语法核对**
  - `type` 被列为新增关键字（保留字），并可通过反引号语法作为标识符使用，例如 `` `type` ``。
  - 当前公开资料未确认 `type` 是否有类型别名声明语法。
- **缺口**
  - 对 `type` 的语义用途未确认，存在“过度实现”的风险。
- **修复目标**
  - 先保证 `type` 作为关键字正确处理与转义（`` `type` ``）。
  - 若规范确认 `type` 声明语法，再补齐 PSI/stub/解析/推断。

### 6. `public struct` 强制
- **现状**
  - `struct` 的 `VisibilityModifier` 是可选项，无强制校验。
  - `MvSyntaxErrorAnnotator` 未对 struct 可见性进行限制。
- **缺口**
  - Move 2024 要求 `public struct`（或至少要求显式可见性）时没有诊断。
- **修复目标**
  - 按 Edition 加入 struct 可见性校验。

### 7. `let mut` 显式可变性
- **现状**
  - 语法允许 `mut`（`PatBinding` / `LetStmt`），但未做语义校验。
  - 无“不可变变量被赋值”诊断。
- **缺口**
  - Move 2024 中“必须显式 `mut`”的规则未实现。
- **修复目标**
  - 新增语义检查：对赋值/可变借用要求绑定为 `mut`（按 Edition 生效）。

### 8. `public(package)` 与 `public(friend)` 变更
- **现状**
  - `VisibilityModifier` 支持 `public(script|package|friend)`。
  - `Visibility2` 仍支持 `friend`。
  - `MvSyntaxErrorAnnotator` 仅在 `enablePublicPackage=false` 时阻止 `public(package)`。
- **Move 2024 语法核对**
  - Move 2024 采用 `public(package)`，并移除 `public(friend)` 与 `friend`。
- **缺口**
  - Move 2024 若废弃 `public(friend)`，目前未做版本级限制。
- **修复目标**
  - 在 Move 2024 edition 中禁止/提示 `public(friend)` 与 `friend` 声明。

### 9. 命名空间与路径规则修订
- **现状**
  - 命名空间划分固定：`Namespace` (NAME/FUNCTION/TYPE/ENUM/SCHEMA/MODULE)。
  - `pathKind` 与解析逻辑未按 Move 2024 修订调整。
- **Move 2024 语法核对**
  - 命名空间调整：类型与“非函数模块成员”共享；函数与名称共享；模块与地址共享。
  - 路径规则调整：全局限定可用 `::` 前缀。
- **缺口**
  - Move 2024 的 namespace/path 规则变更未映射到解析与歧义处理。
- **修复目标**
  - 按最新规则更新 `pathKind`/`Namespace` 组合与解析过滤逻辑。

## 可执行修复计划（分阶段）

### P0：基础设施与 Edition 感知
1. **Move.toml 增加 Edition 解析**
   - 修改：`MoveToml.kt` 读取 `[package].edition`。
   - 新增：`MoveEdition` 枚举（例如 `MOVE_1`, `MOVE_2024_*`）。
2. **项目级特性映射**
   - 新增 `MoveLanguageFeatures`（receiver-style/index/macro/type/visibility/mut 等布尔/枚举）。
   - 在 `MoveProject` 或 `MovePackage` 上缓存 features。
3. **统一 Gate**
   - `MvSyntaxErrorAnnotator` / `MvUnresolvedReferenceInspection` / `TypeInferenceWalker` 等基于 features 而非 UI 的 `moveSettings.*`。
   - UI 设置保留为 override（高级模式），默认由 edition 决定。
4. **Move.toml 诊断**
   - 在 `MoveTomlErrorAnnotator` 增加 edition 字段合法性检查（可选）。

### P1：语法与 PSI 建模补齐（`use fun` / `type` / 宏函数）
1. **`use fun` 语法接入**
   - 修改 BNF：`UseStmt ::= Attr* (UseFun | UseSpeck) ';'`。
   - 新增 PSI 节点：`MvUseFun` / `MvUseFunAlias`（或复用 UseSpeck 结构）。
   - 更新 `MvPsiFactory` / stub / index（若需要）。
2. **`type` 关键字处理**
   - 保证 `type` 在 Move 2024 下为关键字；仅允许 `` `type` `` 作为标识符。
   - 若确认存在 `type` 声明语法，再新增 BNF/PSI/stub/index。
3. **宏函数标记**
   - 在 `MvFunction` 增加 `isMacro` 属性；stub 存储 `MACRO` 标记。
   - 让 `macro` 修饰符在 PSI 中可访问（用于解析/补全/推断）。
    - 新增宏参数命名规则诊断（`$` 前缀与 `$_` 规则）。

### P2：解析/解析/推断/诊断完善
1. **宏解析与调用**
   - 解析：`MacroCallExpr` 的 `path` 解析应能解析到 `macro fun`。
   - 推断：当宏解析到 `macro fun` 时，使用其签名推断参数/返回类型。
   - 诊断：`foo!` 指向非 `macro fun` 时给出错误提示（Move 2024 语义）。
    - 语法：支持 `x.macro!()` 的方法式宏调用（parser + PSI + resolve）。
2. **`use fun` 语义**
   - 解析：将 `use fun` 生成方法别名条目，参与解析与补全。
   - 解析路径与重构：支持 `Optimize Imports` 与 `AutoImport`。
3. **索引语法语义**
   - 根据 `#[syntax(index)]`（及可选分隔符参数）建立可索引函数的解析与诊断。
4. **`type` 声明解析（若规范确认）**
   - 解析：`TypeAlias` 进入 `Namespace.TYPE`。
   - 推断：类型别名展开或在 `TyLowering` 中对 alias 做解析。
5. **公共可见性与 `let mut`**
   - `public struct`：Move 2024 edition 下新增诊断。
   - `let mut`：对可变赋值/可变借用做语义检测。
6. **`public(package)` / `public(friend)`**
   - Move 2024 下禁止 `friend`（含 `friend` 声明与 `public(friend)`）。
   - Move 1 下保持兼容。

### P3：格式化/高亮/补全/测试
1. **高亮**
   - `type` / `macro` / `match` 等关键字高亮校验（`MvHighlighter` 已包含关键字，补充用例）。
2. **格式化**
   - 对 `use fun` 与宏函数调用格式加 spacing/indent 规则。
   - 若确认 `type` 声明语法，再补齐对应格式化规则。
3. **补全**
   - `MacrosCompletionProvider` 加入用户宏。
   - `use fun` 与 `type` 的路径补全与导入提示。
4. **测试**
    - 解析：`src/test/resources/org/sui/lang/parser/complete/` 增加 `type` 关键字 / `use fun` / 宏函数调用 / `public struct` / `let mut` 相关样例。
   - 推断/诊断：新增 `TypeInference` 与 `Inspection` 测试覆盖。

## 具体落地清单（按文件/模块）
> 以下清单用于任务拆分与实现时的落点定位。

- **Move.toml edition**
  - `src/main/kotlin/org/sui/cli/manifest/MoveToml.kt`
  - `src/main/kotlin/org/sui/cli/MoveProject.kt`
  - `src/main/kotlin/org/sui/cli/MovePackage.kt`
  - `src/main/kotlin/org/sui/toml/MoveTomlErrorAnnotator.kt`
- **解析/语法**
  - `src/main/grammars/MoveParser.bnf`（`use fun` / `type` / `macro fun`）
  - `src/main/kotlin/org/sui/lang/core/MoveParserUtil.kt`
  - `src/main/gen`（由 `./gradlew generateParser` 生成）
- **PSI / Stub / Index**
  - `src/main/kotlin/org/sui/lang/core/psi/ext/*`
  - `src/main/kotlin/org/sui/lang/core/stubs/Stubs.kt`
  - `src/main/kotlin/org/sui/lang/core/stubs/StubIndexing.kt`
- **解析与推断**
  - `src/main/kotlin/org/sui/lang/core/resolve2/*`
  - `src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt`
  - `src/main/kotlin/org/sui/lang/core/types/ty/*`
- **诊断/检查**
  - `src/main/kotlin/org/sui/ide/annotator/MvSyntaxErrorAnnotator.kt`
  - `src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt`
  - `src/main/kotlin/org/sui/ide/inspections/*`
- **格式化/高亮/补全**
  - `src/main/kotlin/org/sui/ide/MvHighlighter.kt`
  - `src/main/kotlin/org/sui/ide/formatter/impl/*`
  - `src/main/kotlin/org/sui/lang/core/completion/providers/*`
- **命名空间/路径**
  - `src/main/kotlin/org/sui/lang/core/resolve/ref/Namespace.kt`
  - `src/main/kotlin/org/sui/lang/core/resolve2/ref/MvPath2ReferenceImpl.kt`

## 测试计划（最小闭环）
- **解析**：`CompleteParsingTest` 增加 Move 2024 相关样例文件。
- **语义**：
  - `MvUnresolvedReferenceInspectionTest`：`macro fun` / `use fun` / `type` 解析。
  - `ValueArgumentsNumberErrorTest`：宏签名与参数数校验。
  - 新增 `LetMutRequiredInspectionTest` 与 `PublicStructRequiredInspectionTest`（Move 2024 下开启）。
- **格式化**：新增 `formatter.fixtures` 针对 `type` / `use fun` / `macro fun`。

## 风险与注意事项
- **解析与生成**：`src/main/gen` 不可手改，需 `./gradlew generateParser`。
- **兼容性**：Move 1 与 Move 2024 共存时，Edition gate 必须严格生效。
- **宏/namespace 语义**：需对齐 Sui Move 2024 的最终规范，避免硬编码宏名单。

## 规范核对来源（用于二次确认）
- https://move-book.com/reference/functions.html
- https://move-book.com/reference/macros.html
- https://move-book.com/reference/method-syntax.html
- https://blog.iota.org/move-2024-macros/
- https://github.com/MystenLabs/sui/issues/14062
- https://github.com/MystenLabs/sui/issues/14063
