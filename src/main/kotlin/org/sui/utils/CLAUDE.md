[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **utils**

# 工具类模块

## 模块职责

该模块提供项目级的通用工具类和 UI 组件，包括缓存管理、平台兼容性、签名处理、后台任务队列等功能。

## 关键文件

| 文件 | 功能 |
|------|------|
| `CacheUtils.kt` | 缓存工具函数 |
| `PlatformUtils.kt` | 平台兼容性工具（OS 检测、版本判断） |
| `SignatureUtils.kt` | 函数签名处理工具 |
| `StringUtils.kt` | 字符串处理扩展 |
| `MvBackgroundTaskQueue.kt` | 后台任务队列管理 |
| `AsyncParameterInfoHandlerBase.kt` | 异步参数信息处理基类 |
| `paths.kt` | 路径工具函数 |
| `refactor.kt` | 重构辅助工具 |
| `ui/compat.kt` | UI 兼容性工具 |
| `ui/CompletionTextField.kt` | 带补全功能的文本框组件 |
| `ui/MoveTextFieldWithCompletion.kt` | Move 语言补全文本框 |
| `ui/UiUtils.kt` | UI 工具函数 |

## 依赖关系

- **被依赖方**：cli、ide 模块
- **依赖方**：openapiext、stdext、IntelliJ Platform SDK

## 子目录

- `ui/` - UI 组件和工具，提供自定义的 Swing 组件

## 测试

测试文件位于 `src/test/kotlin/org/sui/utils/`（39 个测试文件）。
