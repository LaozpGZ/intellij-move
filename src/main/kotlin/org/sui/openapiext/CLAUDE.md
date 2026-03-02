[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **openapiext**

# OpenAPI 扩展模块

## 模块职责

该模块提供对 IntelliJ OpenAPI（Platform SDK）的扩展工具集，封装了常用的平台 API 操作，为其他模块提供便捷的基础设施支持，包括命令行执行、文件操作、项目缓存、日志记录等。

## 关键文件

| 文件 | 功能 |
|------|------|
| `CommandLineExt.kt` | 命令行执行扩展，封装 GeneralCommandLine 操作 |
| `MvProcessResult.kt` | 进程执行结果封装 |
| `Project.kt` | Project 扩展函数（获取服务、配置等） |
| `ProjectCache.kt` | 项目级缓存工具，支持 Modification Tracker |
| `Component.kt` | UI 组件扩展 |
| `Editor.kt` | 编辑器扩展函数 |
| `files.kt` | VirtualFile 操作扩展 |
| `paths.kt` | 路径处理工具 |
| `Logger.kt` | 日志工具封装 |
| `PluginPathManager.kt` | 插件路径管理 |
| `StubDataStreams.kt` | Stub 数据流读写工具 |
| `TaskResult.kt` | 异步任务结果封装 |
| `toml.kt` | TOML 文件操作扩展 |
| `ui.kt` | UI 工具函数 |
| `utils.kt` | 通用工具函数 |
| `common/utils.kt` | 公共工具函数 |

## 依赖关系

- **被依赖方**：几乎所有其他模块都依赖此模块（cli、ide、lang、toml、bytecode）
- **依赖方**：IntelliJ Platform SDK

## 设计模式

- 大量使用 Kotlin 扩展函数，为 IntelliJ 平台类型添加便捷方法
- `ProjectCache` 实现了基于 ModificationTracker 的缓存失效机制
- `MvProcessResult` 封装了命令执行的成功/失败结果

## 测试

该模块为基础设施层，主要通过上层模块的集成测试间接覆盖。
