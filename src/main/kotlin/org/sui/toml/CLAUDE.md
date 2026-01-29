[根目录](../../../../../CLAUDE.md) > [src/main/kotlin](../../) > [org.sui](../) > **toml**

# TOML 文件支持模块

## 模块职责

该模块负责对 Sui Move 项目配置文件 `Move.toml` 的支持，提供语法高亮、代码补全、引用解析和错误检查功能。它是项目配置管理的核心组件。

## 入口与启动

### 主要入口类

- `MoveTomlCompletionContributor.kt` - 提供 Move.toml 文件的代码补全功能
- `MoveTomlReferenceContributor.kt` - 提供 Move.toml 文件中的引用解析
- `MoveTomlErrorAnnotator.kt` - 提供 Move.toml 文件的错误检查

### 关键启动流程

```kotlin
// TOML 代码补全配置（plugin.xml）
<completion.contributor language="TOML"
                        implementationClass="org.sui.toml.completion.MoveTomlCompletionContributor"/>

// TOML 引用解析配置（plugin.xml）
<psi.referenceContributor language="TOML"
                          implementation="org.sui.toml.MoveTomlReferenceContributor"/>

// TOML 错误检查配置（plugin.xml）
<annotator language="TOML"
           implementationClass="org.sui.toml.MoveTomlErrorAnnotator"/>
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `MoveToml.kt` | 数据类 | `Move.toml` 文件的解析和表示 |
| `MoveTomlCompletionContributor` | `CompletionContributor` | 代码补全 |
| `MoveTomlReferenceContributor` | `PsiReferenceContributor` | 引用解析 |
| `MoveTomlErrorAnnotator` | `Annotator` | 错误检查 |
| `TomlDependency.kt` | 数据类 | 依赖关系表示 |

### 主要功能接口

- **语法高亮**：为 `Move.toml` 文件提供语法高亮
- **代码补全**：为依赖项、地址和配置项提供补全建议
- **引用解析**：解析 `Move.toml` 文件中的引用
- **错误检查**：检查配置文件中的错误和警告
- **依赖解析**：解析和验证项目依赖
- **配置提取**：从配置文件中提取项目信息

## 关键依赖与配置

### 配置文件解析

```kotlin
data class MoveToml(
    val package: Package? = null,
    val dependencies: Map<String, TomlDependency> = emptyMap(),
    val addresses: Map<String, String> = emptyMap(),
    val devDependencies: Map<String, TomlDependency> = emptyMap(),
    // 其他字段
)

data class TomlDependency(
    val local: String? = null,
    val git: String? = null,
    val subdir: String? = null,
    val rev: String? = null,
    val version: String? = null
)
```

### 代码补全实现

```kotlin
class MoveTomlCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile

        // 为不同的配置节提供补全
        when {
            isInPackageSection(position) -> contributePackageKeys(result)
            isInDependenciesSection(position) -> contributeDependencyKeys(result)
            isInAddressesSection(position) -> contributeAddressKeys(result)
            // 其他情况
        }
    }
}
```

## 数据模型

### Move.toml 文件结构

```
MoveToml
├── package (包信息)
│   ├── name (包名称)
│   ├── version (版本)
│   ├── authors (作者)
│   └── license (许可证)
├── dependencies (依赖项)
│   └── [依赖名称] (TomlDependency)
├── addresses (地址映射)
│   └── [地址名称] = [地址值]
├── devDependencies (开发依赖)
│   └── [依赖名称] (TomlDependency)
└── [其他配置节]
```

### 配置解析流程

```
用户编辑 Move.toml
    ↓
TOML 解析器解析文件
    ↓
MoveToml 数据类表示
    ↓
代码补全和引用解析
    ↓
错误检查和验证
```

## 测试与质量

### 测试位置

目前该模块的测试文件较少，但可以通过以下方式验证功能：

- 手动测试：创建并编辑 `Move.toml` 文件，验证补全和检查功能
- 集成测试：与项目管理模块结合测试

### 验证点

1. 检查语法高亮是否正确
2. 验证依赖项的补全是否工作
3. 测试地址引用的解析
4. 验证错误检查的准确性

## 常见问题 (FAQ)

### 1. Move.toml 文件无法识别怎么办？

- 检查是否已正确安装插件
- 验证 `org.toml.lang` 插件是否已启用（Move 插件依赖此插件）
- 确认文件是否以 `.toml` 扩展名结尾

### 2. 代码补全不工作怎么办？

- 检查项目是否已正确加载
- 验证 `MoveTomlCompletionContributor.kt` 中的补全逻辑
- 确认配置文件格式是否符合规范

### 3. 引用解析失败怎么办？

- 检查引用的目标是否存在
- 验证 `MoveTomlReferenceContributor.kt` 中的解析逻辑
- 确认配置文件语法是否正确

## 相关文件清单

### 主要源代码文件

- `/src/main/kotlin/org/sui/cli/manifest/MoveToml.kt` - `Move.toml` 解析和表示
- `/src/main/kotlin/org/sui/toml/completion/MoveTomlCompletionContributor.kt` - 代码补全
- `/src/main/kotlin/org/sui/toml/MoveTomlReferenceContributor.kt` - 引用解析
- `/src/main/kotlin/org/sui/toml/MoveTomlErrorAnnotator.kt` - 错误检查
- `/src/main/kotlin/org/sui/cli/manifest/TomlDependency.kt` - 依赖关系表示

### 资源文件

- `/src/main/resources/messages/MvBundle.properties` - 国际化资源

## 变更记录 (Changelog)

### 最新版本

- 优化了对复杂 `Move.toml` 结构的解析
- 增强了依赖项的补全功能
- 改进了地址引用的解析准确性
- 添加了对新配置字段的支持

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
