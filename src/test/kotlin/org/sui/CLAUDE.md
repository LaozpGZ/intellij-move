[根目录](../../../../CLAUDE.md) > [src/test/kotlin](../) > **org.sui**

# 测试框架

## 模块职责

该模块包含 Sui Move 语言插件的所有测试，覆盖语法解析、语义分析、代码检查、格式化、导航等核心功能，确保插件的稳定性和正确性。

## 测试框架架构

项目使用 IntelliJ 平台的测试框架，结合 JUnit 和 AssertJ 库进行测试。测试分为多个层次：

1. **单元测试**：测试单个类或方法的功能
2. **集成测试**：测试模块间的交互
3. **功能测试**：测试完整的用户功能
4. **验收测试**：测试实际使用场景

## 测试文件结构

### 目录布局

```
src/test/kotlin/org/sui/
├── cli/               # CLI 集成测试
├── ide/               # IDE 功能测试
├── lang/              # 语言核心功能测试
└── toml/              # TOML 文件支持测试
```

### 主要测试包

| 包名 | 功能描述 | 主要文件 |
|------|----------|----------|
| `cli` | CLI 集成和项目管理测试 | `LoadMoveProjectsTest.kt`, `CompilerErrorsTest.kt` |
| `ide/annotator` | 语法和语义注解测试 | `HighlightingAnnotatorTest.kt`, `errors/*.kt` |
| `ide/inspections` | 代码检查测试 | `MvUnresolvedReferenceInspectionTest.kt`, `MvTypeCheckInspectionTest.kt` |
| `ide/intentions` | 意图动作测试 | `ChopParameterListIntentionTest.kt`, `RemoveCurlyBracesIntentionTest.kt` |
| `ide/formatter` | 代码格式化测试 | `FormatterTest.kt`, `AutoIndentTest.kt` |
| `ide/refactoring` | 重构功能测试 | `RenameTest.kt`, `optimizeImports/*.kt` |
| `lang` | 语言解析和类型系统测试 | `MoveParserTest.kt`, `TypeSystemTest.kt` |
| `toml` | TOML 文件支持测试 | `MoveTomlParsingTest.kt`, `MoveTomlCompletionTest.kt` |

## 测试方法

### 测试框架使用

```kotlin
// 基础测试类
class MvTestCase : LightPlatformCodeInsightFixtureTestCase() {
    override fun getTestDataPath() = TestDataPath
    override fun getBasePath() = TestDataPath
}

// 语法解析测试示例
class MoveParserTest : MvTestCase() {
    fun `test simple module`() {
        val code = """
            module 0x1::test {
                struct S { x: u64 }
                fun foo() {}
            }
        """.trimIndent()

        myFixture.configureByText("test.move", code)
        myFixture.checkHighlighting()
    }
}
```

### 测试数据组织

```
src/test/testData/
├── formatter/            # 格式化测试数据
├── inspections/          # 检查测试数据
├── intentions/          # 意图动作测试数据
├── parser/              # 解析测试数据
└── ...                  # 其他测试数据
```

## 关键测试文件

### CLI 集成测试

- `LoadMoveProjectsTest.kt` - 测试项目加载和解析
- `CompilerErrorsTest.kt` - 测试编译器错误解析
- `MvExternalLinterPassTest.kt` - 测试外部 linter 功能
- `MoveExternalSystemProjectAwareTest.kt` - 测试外部系统项目管理

### IDE 功能测试

- `HighlightingAnnotatorTest.kt` - 测试语法高亮和注释
- `FormatterTest.kt` - 测试代码格式化
- `MvUnresolvedReferenceInspectionTest.kt` - 测试未解析引用检查
- `MvTypeCheckInspectionTest.kt` - 测试类型检查
- `RenameTest.kt` - 测试重命名重构
- `ChopParameterListIntentionTest.kt` - 测试参数列表切分意图
- `RemoveCurlyBracesIntentionTest.kt` - 测试移除大括号意图

### 语言核心测试

- `MoveParserTest.kt` - 测试语法解析
- `MoveLexerTest.kt` - 测试词法分析
- `MvPsiPatternTest.kt` - 测试 PSI 结构
- `TypeSystemTest.kt` - 测试类型系统功能
- `LookupElementTest.kt` - 测试代码补全提示

## 运行测试

### 运行所有测试

```bash
./gradlew test
```

### 运行特定测试类

```bash
./gradlew test --tests "org.sui.lang.MoveParserTest"
```

### 在 IDE 中运行

1. 在 IntelliJ 中打开项目
2. 右键点击测试类或方法
3. 选择 "Run" 或 "Debug"

## 测试覆盖率

项目使用 IntelliJ 的代码覆盖率工具来分析测试覆盖率：

1. 在测试运行配置中启用覆盖率收集
2. 运行测试
3. 查看覆盖率报告

## 常见问题 (FAQ)

### 1. 测试失败怎么办？

- 检查测试数据是否与当前实现匹配
- 验证语法解析和类型系统是否有变化
- 确认是否是外部依赖的问题
- 查看控制台输出获取详细错误信息

### 2. 如何添加新测试？

1. 创建新的测试类，继承自相应的基础类
2. 添加测试方法，使用 `fun` 关键字
3. 实现测试逻辑，使用 `myFixture` 对象
4. 可选：在 `testData` 目录中添加测试数据文件
5. 运行测试验证正确性

### 3. 如何调试测试？

1. 在测试方法中设置断点
2. 右键点击测试方法，选择 "Debug"
3. 执行测试，程序会在断点处暂停
4. 使用调试工具分析变量和执行流程

## 变更记录 (Changelog)

### 最新版本

- 添加了对编译器 V2 新语法的测试
- 优化了测试的执行时间
- 增强了测试的稳定性
- 修复了各种测试失败的问题

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
