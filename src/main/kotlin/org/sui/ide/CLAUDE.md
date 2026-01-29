[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **ide**

# IDE 功能扩展模块

## 模块职责

该模块负责实现 IntelliJ 平台的核心 IDE 功能，为 Sui Move 语言提供代码检查、格式化、导航、补全和重构支持。它是用户与插件交互的主要界面，提供了提升开发效率的关键功能。

## 入口与启动

### 主要入口类

- `MvHighlighter.kt` - 语法高亮器，定义代码的颜色方案
- `MvFormattingModelBuilder.kt` - 代码格式化引擎，负责代码风格格式化
- `MvRefactoringSupportProvider.kt` - 重构支持提供者，定义可用的重构操作

### 关键启动流程

```kotlin
// 语法高亮配置（plugin.xml）
<lang.syntaxHighlighter language="Sui Move"
                        implementationClass="org.sui.ide.MvHighlighter"/>

// 代码格式化配置（plugin.xml）
<lang.formatter language="Sui Move"
                implementationClass="org.sui.ide.formatter.MvFormattingModelBuilder"/>

// 重构支持配置（plugin.xml）
<lang.refactoringSupport language="Sui Move"
                         implementationClass="org.sui.ide.refactoring.MvRefactoringSupportProvider"/>
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `MvHighlighter` | `SyntaxHighlighter` | 语法高亮定义 |
| `MvFormattingModelBuilder` | `FormattingModelBuilder` | 代码格式化引擎 |
| `MvRefactoringSupportProvider` | `RefactoringSupportProvider` | 重构操作支持 |
| `MvUnresolvedReferenceInspection` | `MvLocalInspectionTool` | 未解析引用检查 |
| `MvTypeCheckInspection` | `MvLocalInspectionTool` | 类型检查 |
| `MvAbilityCheckInspection` | `MvLocalInspectionTool` | 能力检查 |
| `MvDocumentationProvider` | `DocumentationProvider` | 文档提示 |

### 主要功能接口

- **语法高亮**：为代码中的不同元素提供颜色区分
- **代码格式化**：自动调整代码风格和缩进
- **代码检查**：实时检查代码中的错误和警告
- **快速修复**：提供自动修复常见问题的功能
- **代码补全**：为标识符、关键字和类型提供补全建议
- **导航功能**：支持跳转到定义、查找使用、结构视图等
- **重构支持**：提供重命名、提取方法等重构操作
- **文档提示**：显示代码元素的文档和类型信息

## 关键依赖与配置

### 配置文件

- `plugin.xml` - 在 `<extensions>` 部分配置了各种 IDE 功能
- `/src/main/resources/colors/MoveDefault.xml` - 默认配色方案
- `/src/main/resources/colors/MoveDarcula.xml` - Dracula 主题配色方案

### 语法高亮配置

```xml
<!-- 插件配置示例 -->
<lang.syntaxHighlighter language="Sui Move"
                        implementationClass="org.sui.ide.MvHighlighter"/>
<additionalTextAttributes scheme="Default" file="colors/MoveDefault.xml"/>
<additionalTextAttributes scheme="Darcula" file="colors/MoveDarcula.xml"/>
```

### 代码格式化配置

```kotlin
class MvFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(
        project: Project,
        file: PsiFile,
        document: Document,
        textRange: TextRange
    ): FormattingModel {
        return FormattingModelProvider.createFormattingModelForPsiFile(
            file,
            MvBlock(
                node = file.node,
                wrap = Wrap.createWrap(WrapType.NONE, false),
                alignment = null,
                indent = Indent.getAbsoluteIndent(),
                textRange = textRange,
                project = project
            ),
            textRange
        )
    }
}
```

## 数据模型

### 检查和修复层次结构

```
MvLocalInspectionTool (基础检查类)
├── MvUnresolvedReferenceInspection (未解析引用检查)
├── MvTypeCheckInspection (类型检查)
├── MvAbilityCheckInspection (能力检查)
├── MvMissingAcquiresInspection (缺少 acquires 检查)
├── MvUnusedImportInspection (未使用导入检查)
└── 其他检查类
    └── 快速修复 (IntentionAction)
```

### 高亮属性定义

```xml
<!-- colors/MoveDefault.xml 示例 -->
<attributes>
  <attribute name="KEYWORD" foreground="000080" font-style="bold"/>
  <attribute name="STRING" foreground="008000"/>
  <attribute name="COMMENT" foreground="808080" font-style="italic"/>
  <attribute name="IDENTIFIER" foreground="000000"/>
  <!-- 其他属性 -->
</attributes>
```

## 测试与质量

### 测试位置

测试文件位于 `src/test/kotlin/org/sui/ide/` 目录下，主要测试以下内容：

- **语法高亮**：验证高亮属性是否正确应用
- **代码格式化**：测试格式化规则的正确性
- **代码检查**：验证检查规则的准确性
- **快速修复**：测试自动修复的正确性
- **导航功能**：测试跳转到定义等功能

### 关键测试文件

- `HighlightingAnnotatorTest.kt` - 测试语法高亮和注释
- `FormatterTest.kt` - 测试代码格式化
- `MvUnresolvedReferenceInspectionTest.kt` - 测试未解析引用检查
- `MvTypeCheckInspectionTest.kt` - 测试类型检查
- `RenameTest.kt` - 测试重命名重构

## 常见问题 (FAQ)

### 1. 语法高亮不正确怎么办？

- 检查是否使用了正确的配色方案
- 验证 `MoveDefault.xml` 和 `MoveDarcula.xml` 文件是否完整
- 确认 `MvHighlighter.kt` 中的高亮规则是否正确

### 2. 代码格式化不工作怎么办？

- 检查格式化设置是否正确配置
- 验证 `MvFormattingModelBuilder.kt` 是否正确实现
- 确保代码结构符合语法规范

### 3. 代码检查未显示警告怎么办？

- 检查检查是否启用（Settings > Editor > Inspections > Sui Move）
- 验证检查类是否正确实现
- 确认代码是否符合检查规则

## 相关文件清单

### 主要源代码文件

- `/src/main/kotlin/org/sui/ide/MvHighlighter.kt` - 语法高亮器
- `/src/main/kotlin/org/sui/ide/formatter/MvFormattingModelBuilder.kt` - 代码格式化引擎
- `/src/main/kotlin/org/sui/ide/inspections/` - 各种代码检查实现
- `/src/main/kotlin/org/sui/ide/refactoring/` - 重构操作实现
- `/src/main/kotlin/org/sui/ide/hints/` - 代码提示和参数信息
- `/src/main/kotlin/org/sui/ide/lineMarkers/` - 行标记提供者
- `/src/main/kotlin/org/sui/ide/folding/` - 代码折叠支持
- `/src/main/kotlin/org/sui/ide/annotator/` - 语义注释器
- `/src/main/kotlin/org/sui/ide/structureView/` - 结构视图实现

### 资源文件

- `/src/main/resources/colors/MoveDefault.xml` - 默认配色方案
- `/src/main/resources/colors/MoveDarcula.xml` - Dracula 主题配色方案
- `/src/main/resources/liveTemplates/Move.xml` - 实时模板
- `/src/main/resources/icons/` - 图标资源

## 变更记录 (Changelog)

### 最新版本

- 优化了代码格式化规则，支持更多配置选项
- 增强了代码检查的准确性和性能
- 添加了新的快速修复功能
- 改进了代码补全的建议排序

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
