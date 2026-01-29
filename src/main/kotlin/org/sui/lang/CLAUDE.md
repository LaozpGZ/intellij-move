[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **lang**

# 核心语言支持模块

## 模块职责

该模块负责 Sui Move 语言的核心解析和表示，包括语法解析、词法分析、PSI（Program Structure Interface）结构定义以及类型系统实现。它是插件的基础组件，为所有其他功能提供语言层面的支持。

## 入口与启动

### 主要入口类

- `MoveLanguage.kt` - 语言定义类，继承自 `Language` 类，是 IntelliJ 平台识别 Move 语言的入口点
- `MoveFileType.kt` - 文件类型定义类，继承自 `LanguageFileType` 类，用于识别 `.move` 文件
- `MoveParserDefinition.kt` - 解析器定义类，实现了 `ParserDefinition` 接口，负责创建词法分析器和语法解析器

### 关键启动流程

```kotlin
// 语言定义
object MoveLanguage : Language("Sui Move")

// 文件类型定义
object MoveFileType : LanguageFileType(MoveLanguage) {
    override fun getIcon() = MoveIcons.SUI_LOGO
    override fun getName() = "Sui Move"
    override fun getDefaultExtension() = "move"
    override fun getDescription() = "Move Language file"
}

// 解析器定义
class MoveParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer {
        return createMoveLexer()
    }

    override fun createParser(project: Project): PsiParser {
        return MoveParser()
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return MoveFile(viewProvider)
    }
}
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `MoveLanguage` | `Language` | 语言标识符，用于 IntelliJ 平台识别 |
| `MoveFileType` | `LanguageFileType` | 文件类型识别，与 `.move` 文件关联 |
| `MoveParserDefinition` | `ParserDefinition` | 解析器工厂，创建词法和语法解析器 |
| `MoveParser` | `PsiParser` | 语法解析器，生成 AST（抽象语法树） |
| `MoveFile` | `PsiFile` | Move 文件的 PSI 表示 |

### 主要功能接口

- **词法分析**：通过 `MvLexer` 类实现，将输入文本分解为令牌流
- **语法分析**：通过 `MoveParser` 类实现，将令牌流解析为 AST
- **PSI 构建**：通过解析器和 PSI 元素工厂创建代码的结构化表示
- **类型系统**：分析代码的类型信息，支持类型推断和验证

## 关键依赖与配置

### 语法定义文件

- `MoveLexer.flex` - 词法分析器定义，使用 JFlex 生成
- `MoveParser.bnf` - 语法解析器定义，使用 Grammar-Kit 生成

### 配置文件

- `plugin.xml` - 在 `<extensions>` 部分配置了语言支持相关的扩展点

## 数据模型

### PSI 元素层次结构

```
MoveFile (根元素)
├── MvModule (模块)
│   ├── MvStruct (结构体)
│   ├── MvFunction (函数)
│   └── MvConstant (常量)
└── MvUseStmt (导入声明)
    └── MvPath (路径)
```

### 主要 PSI 元素

- **MvFile** - 代表整个 Move 文件
- **MvModule** - 代表模块声明
- **MvStruct** - 代表结构体类型
- **MvFunction** - 代表函数定义
- **MvConstant** - 代表常量定义
- **MvPath** - 代表引用路径
- **MvExpr** - 代表表达式（包含各种子类型）
- **MvStmt** - 代表语句（包含各种子类型）

## 测试与质量

### 测试位置

测试文件位于 `src/test/kotlin/org/sui/lang/` 目录下，主要测试以下内容：

- **语法解析**：验证解析器是否能正确解析 Move 代码
- **词法分析**：验证分词器是否能正确识别各种令牌
- **PSI 结构**：验证代码结构的 PSI 表示是否正确
- **类型系统**：验证类型推断和类型检查的正确性

### 关键测试文件

- `MoveParserTest.kt` - 测试语法解析
- `MoveLexerTest.kt` - 测试词法分析
- `MvPsiPatternTest.kt` - 测试 PSI 结构
- `TypeSystemTest.kt` - 测试类型系统功能

## 常见问题 (FAQ)

### 1. 语法解析错误如何处理？

如果遇到语法解析错误，通常是由于以下原因：

- Move 语法发生了变化，需要更新 `MoveParser.bnf` 文件
- 词法分析器无法识别某些令牌，需要更新 `MoveLexer.flex` 文件
- 解析器生成的代码过期，需要重新生成解析器

### 2. 如何添加新的语法结构？

1. 修改 `MoveParser.bnf` 文件，添加新的语法规则
2. 修改 `MoveLexer.flex` 文件（如果需要添加新的令牌类型）
3. 运行 `./gradlew generateParser` 重新生成解析器代码
4. 在 `MvElementTypes.kt` 中添加新的元素类型定义
5. 创建或修改相应的 PSI 元素类

## 相关文件清单

### 主要源代码文件

- `/src/main/grammars/MoveLexer.flex` - 词法分析器定义
- `/src/main/grammars/MoveParser.bnf` - 语法解析器定义
- `/src/main/kotlin/org/sui/lang/MoveLanguage.kt` - 语言定义
- `/src/main/kotlin/org/sui/lang/MoveFileType.kt` - 文件类型定义
- `/src/main/kotlin/org/sui/lang/MoveParserDefinition.kt` - 解析器定义
- `/src/main/kotlin/org/sui/lang/core/psi/MvPsiElementTypes.kt` - PSI 元素类型定义
- `/src/main/kotlin/org/sui/lang/core/psi/MvPsiFactory.kt` - PSI 元素工厂
- `/src/main/kotlin/org/sui/lang/core/psi/MvFile.kt` - 文件 PSI 类
- `/src/main/kotlin/org/sui/lang/core/resolve/` - 引用解析实现
- `/src/main/kotlin/org/sui/lang/core/types/` - 类型系统实现

### 资源文件

- `/src/main/resources/icons/` - 语言图标资源
- `/src/main/resources/colors/` - 语法高亮颜色配置

## 变更记录 (Changelog)

### 最新版本

- 添加了对编译器 V2 新语法的支持
- 优化了类型系统性能
- 修复了各种语法解析错误

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
