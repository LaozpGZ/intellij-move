
上一轮的主线基本已经完成了（use fun、宏语义收敛、Move 2024 gate、导入链路、match 重复分支）——但如果目标是“可实用 + 新特性可靠支持”，还有这些必要缺口。

  必须补齐（P0，发布前）

  - type 新特性还没形成声明级闭环：目前语法里只有 token，模块 item 规则里没有 type 声明入口（ModuleItem_first/ModuleItem 未包含）。见 src/main/grammars/MoveParser.bnf:156、
    src/main/grammars/MoveParser.bnf:274。
  - match 诊断仍是 MVP：现在只做了“重复分支”，还缺穷尽性、不可达分支等高价值诊断。见 src/main/kotlin/org/sui/ide/annotator/MvErrorAnnotator.kt:201。
  - 新增 feature override 没有完整 UI 暴露：设置模型里已有 enableMacroFunctions/enableTypeKeyword/requirePublicStruct/requireLetMut，但配置页仅展示旧的几个开关。见 src/main/
    kotlin/org/sui/cli/settings/MvProjectSettingsService.kt:95、src/main/kotlin/org/sui/cli/settings/PerProjectSuiConfigurable.kt:47、src/main/kotlin/org/sui/cli/settings/
    PerProjectAptosConfigurable.kt:47。
  - 质量门禁还差全量回归：你文档把 ./gradlew test 全量通过列为强制项，当前主要是定向回归。见 docs/testing-checklist.md:26、docs/testing-checklist.md:85。

  应尽快补齐（P1，强烈建议）

  - 宏链路细节一致性：unresolved 在宏调用下仍用 function 文案（体验不一致）。见 src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt:176。
  - 宏补全未显式受 macroFunctions gate 约束（建议补负向测试并收口行为）。见 src/main/kotlin/org/sui/lang/core/completion/providers/MacrosCompletionProvider.kt:69。
  - 若你要求 Aptos 测试运行配置“可实用”：对应 producer 当前整文件注释，需确认是否恢复或替代实现。见 src/main/kotlin/org/sui/cli/runConfigurations/producers/aptos/
    AptosTestCommandConfigurationProducer.kt:4。

  可后置（P2，测试债）

  - 仍有若干历史 TODO 测试空洞（非本轮主线，但长期会影响稳定性）。见 src/test/kotlin/org/sui/ide/annotator/syntaxErrors/AllowedSpecStatementsTest.kt:3、src/test/kotlin/org/
    sui/lang/types/AcquiresTypesTest.kt:255。

  如果你同意，我建议下一步按这个顺序托管推进：

  1. type 声明闭环；2) match 穷尽性最小版；3) 设置页补齐新开关；4) 全量回归 + verifyPlugin。