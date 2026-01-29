[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **cli**

# CLI 集成模块

## 模块职责

该模块负责与 Sui 和 Aptos 命令行工具的集成，提供项目管理、构建、配置和外部 linter 功能。它是插件与外部工具交互的核心组件，为其他模块提供项目上下文和环境信息。

## 入口与启动

### 主要入口类

- `MoveProjectsService.kt` - 项目服务类，负责管理和刷新项目状态
- `MvProjectSettingsService.kt` - 项目设置服务类，负责管理插件的全局和项目级设置
- `BuildDirectoryWatcher.kt` - 构建目录监听器，负责监视项目构建文件变化

### 关键启动流程

```kotlin
// 获取项目服务实例
val project: Project = ...
val moveProjectsService = project.moveProjectsService

// 刷新项目信息
moveProjectsService.scheduleProjectsRefresh("User requested refresh")

// 获取项目设置
val projectSettings = project.moveSettings

// 访问 CLI 执行路径
val suiExecType = projectSettings.suiExecType
val localSuiPath = projectSettings.localSuiPath
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `MoveProjectsService` | `Disposable` | 管理项目状态和刷新 |
| `MvProjectSettingsService` | `MvProjectSettingsServiceBase` | 管理插件设置 |
| `BuildDirectoryWatcher` | `Disposable` | 监视项目构建文件变化 |
| `MoveProject` | 数据类 | 表示一个 Move 项目 |
| `MovePackage` | 数据类 | 表示一个 Move 包 |
| `PackageAddresses` | 数据类 | 包地址管理 |

### 主要功能接口

- **项目管理**：加载和刷新项目信息，管理项目包结构
- **设置管理**：管理 Sui 和 Aptos CLI 路径、网络配置等
- **依赖解析**：解析 `Move.toml` 文件中的依赖关系
- **构建集成**：与 Sui 和 Aptos 构建系统交互
- **外部 Linter**：使用外部编译器进行代码检查
- **命令执行**：执行 Sui 和 Aptos 命令并处理输出

## 关键依赖与配置

### 配置文件

- `plugin.xml` - 配置项目服务和设置页面
- `src/main/resources/META-INF/plugin.xml` - 声明扩展点和服务

### 主要配置类

```kotlin
class MoveProjectSettings : MvProjectSettingsBase<MoveProjectSettings>() {
    var aptosExecType: AptosExecType by enum(defaultAptosExecType)
    var localAptosPath: String? by string()
    var suiExecType: SuiExecType by enum(defaultSuiExecType)
    var localSuiPath: String? by string()
    var enableReceiverStyleFunctions: Boolean by property(true)
    var enableResourceAccessControl: Boolean by property(false)
    // ... 其他设置
}
```

### 项目结构相关

```kotlin
class MoveProject(
    val rootDirectory: Path,
    val manifestFile: Path,
    val packages: List<MovePackage>
)

class MovePackage(
    val root: Path,
    val manifest: MoveToml,
    val sources: List<Path>,
    val dependencies: List<MovePackage>
)
```

## 数据模型

### 项目配置层次结构

```
MvProjectSettingsService (插件设置)
├── MoveProjectSettings (项目级设置)
│   ├── SuiExecType (Sui CLI 执行类型)
│   ├── localSuiPath (Sui CLI 路径)
│   ├── AptosExecType (Aptos CLI 执行类型)
│   ├── localAptosPath (Aptos CLI 路径)
│   └── 其他功能开关
└── PerProjectSuiConfigurable (设置界面)
    └── MvProjectSettingsService (服务实例)
```

### 项目状态管理

```
MoveProjectsService (项目服务)
├── projects (项目状态)
│   ├── allProjects (所有已加载的项目)
│   └── hasAtLeastOneValidProject (是否有有效项目)
├── initialized (初始化状态)
└── scheduleProjectsRefresh (刷新项目方法)
```

## 测试与质量

### 测试位置

测试文件位于 `src/test/kotlin/org/sui/cli/` 目录下，主要测试以下内容：

- **项目加载**：测试项目解析和加载过程
- **设置管理**：测试设置的保存和加载
- **外部 Linter**：测试与外部编译器的交互
- **项目刷新**：测试项目状态变化的响应

### 关键测试文件

- `LoadMoveProjectsTest.kt` - 测试项目加载和解析
- `CompilerErrorsTest.kt` - 测试编译器错误解析
- `MvExternalLinterPassTest.kt` - 测试外部 linter 功能
- `MoveExternalSystemProjectAwareTest.kt` - 测试外部系统项目管理

## 常见问题 (FAQ)

### 1. 项目无法加载怎么办？

如果项目无法加载，可能的原因包括：

- `Move.toml` 文件格式不正确
- 缺少依赖包
- Sui CLI 路径配置错误
- 项目结构不符合 Move 语言规范

### 2. 如何配置 Sui CLI 路径？

1. 打开 `Settings > Languages & Frameworks > Sui Move Language`
2. 在 "Sui CLI" 部分选择执行类型
3. 如果选择 "Local"，需提供 Sui CLI 可执行文件的完整路径
4. 点击 "Apply" 保存设置

### 3. 外部 Linter 不工作怎么办？

- 确保已正确配置 Sui 或 Aptos CLI 路径
- 检查项目已正确构建（运行 `move package build`）
- 验证网络连接（如果使用远程执行）

## 相关文件清单

### 主要源代码文件

- `/src/main/kotlin/org/sui/cli/MoveProjectsService.kt` - 项目服务类
- `/src/main/kotlin/org/sui/cli/settings/MvProjectSettingsService.kt` - 项目设置服务类
- `/src/main/kotlin/org/sui/cli/settings/PerProjectSuiConfigurable.kt` - 设置界面类
- `/src/main/kotlin/org/sui/cli/MovePackage.kt` - 包管理类
- `/src/main/kotlin/org/sui/cli/manifest/MoveToml.kt` - Move.toml 文件解析
- `/src/main/kotlin/org/sui/cli/externalLinter/ExternalLinter.kt` - 外部 linter 实现
- `/src/main/kotlin/org/sui/cli/BuildDirectoryWatcher.kt` - 构建目录监听器
- `/src/main/kotlin/org/sui/cli/LibraryRootsProvider.kt` - 库根路径提供器
- `/src/main/kotlin/org/sui/cli/ProcessProgressListener.kt` - 进程进度监听

### 资源文件

- `/src/main/resources/messages/MvBundle.properties` - 国际化资源
- `/src/main/resources/icons/` - 图标资源

## 变更记录 (Changelog)

### 最新版本

- 优化了项目加载和依赖解析的性能
- 增强了对 `Move.toml` 文件格式变化的兼容性
- 添加了对 Sui CLI 0.28.0 版本的支持

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
