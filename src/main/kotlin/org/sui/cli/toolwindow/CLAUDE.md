[根目录](../../../../../CLAUDE.md) > [src/main/kotlin](../../) > [org.sui](../) > [cli](./) > **toolwindow**

# 工具窗口模块

## 模块职责

该模块负责实现 Sui 项目管理的可视化工具窗口，提供项目刷新、账户管理、网络切换和对象查看等功能，是用户与 Sui 区块链交互的主要界面。

## 入口与启动

### 主要入口类

- `SuiToolWindowFactory.kt` - 工具窗口工厂类，负责创建 Sui 工具窗口
- `SuiToolWindow.kt` - 工具窗口主类，管理工具窗口的内容和布局
- `SuiToolWindowView.kt` - 工具窗口视图类，负责 UI 渲染

### 关键启动流程

```kotlin
// 工具窗口配置（plugin.xml）
<toolWindow id="Sui"
            anchor="right"
            factoryClass="org.sui.cli.toolwindow.SuiToolWindowFactory"
            icon="/icons/sui.svg"/>
```

## 对外接口

### 核心组件

| 组件 | 类型 | 功能 |
|------|------|------|
| `SuiToolWindowFactory` | `ToolWindowFactory` | 工具窗口工厂 |
| `SuiToolWindow` | 容器类 | 工具窗口主体 |
| `SuiToolWindowView` | UI 组件 | 工具窗口视图 |
| `SuiToolWindowViewModel` | 数据类 | 工具窗口数据模型 |

### 主要功能接口

- **项目刷新**：同步项目状态和依赖关系
- **账户管理**：显示和切换活跃账户
- **网络管理**：显示和切换网络环境
- **对象查看**：查看和管理 Sui 对象
- **气体管理**：获取和显示气体价格信息
- **快速操作**：提供常用操作的快捷方式

## 关键依赖与配置

### 工具窗口创建

```kotlin
class SuiToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowView = SuiToolWindowView(project)
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(
                toolWindowView.component,
                "",
                false
            )
        )
    }
}
```

### 工具窗口视图

```kotlin
class SuiToolWindowView(project: Project) {
    val component: JComponent

    init {
        // 创建工具窗口 UI 组件
        component = JPanel(BorderLayout())

        // 添加工具栏
        val toolbar = createToolbar()
        component.add(toolbar, BorderLayout.NORTH)

        // 添加内容区域
        val content = createContentArea()
        component.add(content, BorderLayout.CENTER)

        // 初始化数据
        initializeData()
    }

    // 其他方法
}
```

## 数据模型

### 工具窗口状态

```
SuiToolWindowViewModel (数据模型)
├── activeProject (活跃项目)
├── activeAddress (活跃地址)
├── currentNetwork (当前网络)
├── objects (对象列表)
├── gasPrice (气体价格)
└── isLoading (加载状态)
```

### 操作流程

```
用户打开工具窗口 → 初始化 → 加载项目状态 → 显示 UI
    ↓
用户执行操作 → 更新状态 → 刷新 UI
    ↓
操作完成 → 显示结果
```

## 测试与质量

### 测试位置

测试文件位于 `src/test/kotlin/org/sui/cli/toolwindow/` 目录下，主要测试以下内容：

- **工具窗口创建**：测试工具窗口是否能正常创建和显示
- **项目状态**：验证项目状态的加载和刷新
- **操作响应**：测试操作的执行和响应
- **UI 交互**：测试 UI 组件的交互

### 关键测试文件

- `MoveProjectsStructureTest.kt` - 测试项目结构显示

## 常见问题 (FAQ)

### 1. 工具窗口无法打开怎么办？

- 检查是否已正确安装插件
- 验证 Sui CLI 路径是否正确配置
- 确认项目是否已正确加载
- 尝试重启 IDE

### 2. 工具窗口显示为空怎么办？

- 检查项目配置是否正确
- 验证网络连接是否正常
- 尝试点击"刷新"按钮
- 查看 IDE 日志获取详细信息

### 3. 账户信息无法加载怎么办？

- 检查 Sui CLI 是否正确配置
- 验证网络连接是否正常
- 确认钱包是否已正确设置
- 尝试重新连接网络

## 相关文件清单

### 主要源代码文件

- `/src/main/kotlin/org/sui/cli/toolwindow/SuiToolWindowFactory.kt` - 工具窗口工厂
- `/src/main/kotlin/org/sui/cli/toolwindow/SuiToolWindow.kt` - 工具窗口主类
- `/src/main/kotlin/org/sui/cli/toolwindow/SuiToolWindowView.kt` - 工具窗口视图
- `/src/main/kotlin/org/sui/cli/toolwindow/SuiToolWindowViewModel.kt` - 数据模型

### 资源文件

- `/src/main/resources/icons/sui.svg` - 工具窗口图标
- `/src/main/resources/messages/MvBundle.properties` - 国际化资源

## 变更记录 (Changelog)

### 最新版本

- 添加了对 Sui CLI 0.28.0 的支持
- 优化了工具窗口的响应速度
- 改进了网络切换的用户体验
- 增强了错误处理和用户反馈

### 历史版本

- 1.6.0：重写了类型系统，提升了准确性和性能
- 1.5.0：添加了对 Move 字节码的反编译支持
- 1.4.0：增强了代码格式化和导航功能
- 1.3.0：添加了对 Aptos 网络的支持
- 1.2.0：优化了项目加载和依赖解析
- 1.1.0：添加了代码检查和快速修复功能
- 1.0.0：初始版本，包含基本语法高亮和导航功能
