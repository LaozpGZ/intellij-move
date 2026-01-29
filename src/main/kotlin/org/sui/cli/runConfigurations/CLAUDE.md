[根目录](../../../../../CLAUDE.md) > [src/main/kotlin](../../) > [org.sui](../) > [cli](./) > **runConfigurations**

# 运行配置模块

## 模块职责

该模块负责实现 Sui 和 Aptos 命令的运行配置，允许用户在 IntelliJ 中直接配置和执行 Sui Move 相关的命令，包括构建、测试、部署等操作。

## 入口与启动

### 主要入口类

- `SuiCommandConfigurationType.kt` - Sui 命令配置类型，定义 Sui 命令的配置入口
- `AptosCommandConfigurationType.kt` - Aptos 命令配置类型，定义 Aptos 命令的配置入口
- `CommandConfigurationBase.kt` - 命令配置基类，提供共同的配置属性

### 关键启动流程

```kotlin
// Sui 命令配置类型（plugin.xml）
<configurationType
        implementation="org.sui.cli.runConfigurations.sui.SuiCommandConfigurationType"/>

// 运行配置生成器（plugin.xml）
<runConfigurationProducer
        implementation="org.sui.cli.runConfigurations.producers.sui.SuiTestCommandConfigurationProducer"/>
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `SuiCommandConfigurationType` | `ConfigurationTypeBase` | Sui 命令配置类型 |
| `AptosCommandConfigurationType` | `ConfigurationTypeBase` | Aptos 命令配置类型 |
| `CommandConfigurationBase` | `RunConfigurationBase` | 命令配置基类 |
| `SuiCommandConfiguration` | `CommandConfigurationBase` | Sui 命令配置类 |
| `AptosCommandConfiguration` | `CommandConfigurationBase` | Aptos 命令配置类 |
| `SuiCommandConfigurationFactory` | `ConfigurationFactory` | Sui 命令配置工厂 |
| `AptosCommandConfigurationFactory` | `ConfigurationFactory` | Aptos 命令配置工厂 |
| `RunSuiCommandActionBase` | `AnAction` | Sui 命令执行基类 |

### 主要功能接口

- **命令配置**：定义和保存命令参数
- **命令执行**：启动和管理命令的执行
- **输出处理**：解析和显示命令输出
- **运行状态**：管理运行过程中的状态信息
- **测试运行**：支持运行 Move 测试
- **配置生成**：根据上下文自动生成配置

## 关键依赖与配置

### 配置文件

- `plugin.xml` - 在 `<extensions>` 部分配置了运行配置类型和生成器

### 命令配置基类

```kotlin
abstract class CommandConfigurationBase(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var commandLine: String
        get() = options.commandLine
        set(value) {
            options.commandLine = value
        }

    var workingDirectory: String
        get() = options.workingDirectory
        set(value) {
            options.workingDirectory = value
        }

    // 其他共同属性
}
```

### 命令执行状态

```kotlin
class SuiRunState(
    runProfile: RunProfile,
    executionEnvironment: ExecutionEnvironment
) : CommandLineState(executionEnvironment) {

    override fun startProcess(): ProcessHandler {
        val commandLine = createCommandLine()
        return MvCapturingProcessHandler(commandLine)
    }

    // 其他方法
}
```

## 数据模型

### 运行配置层次结构

```
CommandConfigurationBase (基础配置类)
├── SuiCommandConfiguration (Sui 命令配置)
│   ├── commandLine (命令行参数)
│   ├── workingDirectory (工作目录)
│   └── suiExecType (Sui CLI 执行类型)
├── AptosCommandConfiguration (Aptos 命令配置)
│   ├── commandLine (命令行参数)
│   ├── workingDirectory (工作目录)
│   └── aptosExecType (Aptos CLI 执行类型)
└── TestCommandConfiguration (测试命令配置)
    ├── testFilters (测试过滤条件)
    └── testScope (测试范围)
```

### 命令执行流程

```
用户创建配置 → 配置参数验证 → 命令构建 → 进程启动 → 输出解析 → 结果显示
    ↓                ↓                ↓                ↓                ↓
CommandConfiguration → CommandLineBuilder → ProcessHandler → OutputParser → ConsoleView
```

## 测试与质量

### 测试位置

测试文件位于 `src/test/kotlin/org/sui/cli/runConfigurations/` 目录下，主要测试以下内容：

- **配置创建**：测试运行配置的创建过程
- **命令构建**：验证命令参数的解析和构建
- **命令执行**：测试实际命令的执行和输出处理
- **配置生成**：测试根据上下文自动生成配置的功能

### 关键测试文件

- `CommandConfigurationHandlerTest.kt` - 测试命令配置处理
- `TestCommandConfigurationProducerTest.kt` - 测试测试命令配置生成器
- `SuiCommandLineFromContextTest.kt` - 测试 Sui 命令行构建

## 常见问题 (FAQ)

### 1. 如何创建运行配置？

1. 点击顶部工具栏的 "Run" 菜单
2. 选择 "Edit Configurations..."
3. 点击左上角的 "+" 按钮
4. 选择 "Sui Command" 或 "Aptos Command"
5. 配置命令参数和工作目录
6. 点击 "OK" 保存配置

### 2. 命令执行失败怎么办？

- 检查命令参数是否正确
- 验证工作目录是否设置为项目根目录
- 确认 Sui 或 Aptos CLI 路径是否配置正确
- 查看控制台输出获取详细错误信息

### 3. 测试命令无法运行怎么办？

- 检查测试文件是否符合 Move 语言规范
- 验证项目已正确构建（运行 `move package build`）
- 确认测试命令配置正确
- 检查网络连接（如果使用远程测试）

## 相关文件清单

### 主要源代码文件

- `/src/main/kotlin/org/sui/cli/runConfigurations/CommandConfigurationBase.kt` - 命令配置基类
- `/src/main/kotlin/org/sui/cli/runConfigurations/sui/SuiCommandConfigurationType.kt` - Sui 配置类型
- `/src/main/kotlin/org/sui/cli/runConfigurations/aptos/AptosCommandConfigurationType.kt` - Aptos 配置类型
- `/src/main/kotlin/org/sui/cli/runConfigurations/sui/SuiRunState.kt` - Sui 运行状态
- `/src/main/kotlin/org/sui/cli/runConfigurations/aptos/AptosRunState.kt` - Aptos 运行状态
- `/src/main/kotlin/org/sui/cli/runConfigurations/producers/` - 配置生成器实现
- `/src/main/kotlin/org/sui/cli/runConfigurations/test/` - 测试运行支持
- `/src/main/kotlin/org/sui/cli/runConfigurations/buildtool/` - 构建工具支持

### 资源文件

- `/src/main/resources/icons/` - 配置类型图标
- `/src/main/resources/messages/MvBundle.properties` - 国际化资源

## 变更记录 (Changelog)

### 最新版本

- 添加了对 Sui CLI 0.28.0 命令的支持
- 优化了命令执行的错误处理
- 改进了测试命令的输出解析
- 增强了配置生成的准确性

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
