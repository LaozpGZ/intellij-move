[根目录](../../../../CLAUDE.md) > [src/main/kotlin](../) > [org.sui](../../) > **stdext**

# 标准库扩展模块

## 模块职责

该模块提供 Kotlin 标准库的扩展函数和工具类，补充标准库中缺失的常用功能，为整个项目提供基础数据结构和并发工具支持。

## 关键文件

| 文件 | 功能 |
|------|------|
| `Collections.kt` | 集合操作扩展（过滤、映射、分组等便捷方法） |
| `Concurrency.kt` | 并发工具（线程安全操作、锁封装） |
| `FileToMoveProjectCache.kt` | 文件到 MoveProject 的缓存映射 |
| `Paths.kt` | 路径操作扩展函数 |
| `RsResult.kt` | Result 类型封装（类似 Rust 的 Result<T, E>） |
| `RunnerAndConfigurationSettings.kt` | 运行配置设置扩展 |
| `Utils.kt` | 通用工具函数 |

## 依赖关系

- **被依赖方**：所有模块（最底层的工具模块）
- **依赖方**：Kotlin 标准库、IntelliJ Platform SDK（少量）

## 设计模式

- `RsResult` 采用密封类实现，提供 `Ok` 和 `Err` 两个子类型，支持链式操作
- 扩展函数风格，不引入额外的类层次结构

## 测试

基础工具模块，通过上层模块测试间接覆盖。
