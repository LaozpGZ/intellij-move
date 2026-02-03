# Repository Guidelines

## Project Structure & Module Organization
该仓库是 IntelliJ 平台插件工程，基于 Gradle/Kotlin 构建。核心代码位于 `src/main/kotlin` 与 `src/main/java`，资源文件在 `src/main/resources`（如 `META-INF`、icons、inspectionDescriptions）。语法/解析相关文件在 `src/main/grammars`，生成代码输出到 `src/main/gen`（由任务生成，勿手改）。测试位于 `src/test/kotlin`，测试数据与样例项目在 `src/test/resources`。`ui-tests/` 是独立 UI 测试模块目录，但在 `settings.gradle.kts` 中未默认启用。文档与变更信息在 `docs/` 与 `changelog/`。

## Build, Test, and Development Commands
- `./gradlew build`：编译与打包插件（含测试）。
- `./gradlew test`：运行单元测试（JUnit4）。
- `./gradlew runIde`：启动带插件的沙盒 IDE 进行本地调试。
- `./gradlew verifyPlugin`：运行 JetBrains Plugin Verifier。
- `./gradlew buildPlugin`：生成可发布的插件包。
- `./gradlew generateLexer generateParser`：更新语法/解析器生成代码。

## Coding Style & Naming Conventions
代码以 Kotlin/Java 为主，采用 IntelliJ 默认格式：4 空格缩进、无 Tab。包名遵循 `org.sui...`，类名/文件名使用 `PascalCase`。测试类以 `*Test.kt` 结尾。`src/main/gen` 为生成目录，不直接编辑；如需修改，先调整 `src/main/grammars` 并重新生成。

## Testing Guidelines
测试框架为 JUnit4，测试代码在 `src/test/kotlin`，测试资源在 `src/test/resources`。新增测试时保持 `*Test.kt` 命名，并将样例 Move 项目或固定输入放到对应资源目录。运行测试使用 `./gradlew test`，如涉及解析/格式化，建议补充资源样例。

## Commit & Pull Request Guidelines
Git 历史中常见前缀有 `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `debug:`；建议继续使用该风格，动词保持现在时、描述清晰。PR 需包含变更说明、测试结果（含命令），UI/交互改动请附截图或录屏，并在涉及用户可见功能时更新 `changelog/`。

## Security & Configuration Tips
发布相关凭据通过环境变量提供（如 `JB_PUB_TOKEN`、`JB_PUB_CHANNEL`），不要提交到仓库。平台版本可通过 `ORG_GRADLE_PROJECT_shortPlatformVersion` 覆盖，避免在代码中硬编码环境差异。

## 参考与对标资料（按模块映射）
### 解析/语法与 PSI 结构
- `https://github.com/pontem-network/intellij-move`：对标 Move 语法解析、PSI 结构与语义树组织。

### 类型推断与静态分析
- `https://github.com/movebit/sui-move-analyzer`：对标类型推断、诊断规则与分析器能力边界。

### 格式化/高亮与编辑器体验
- `https://github.com/pontem-network/intellij-move`：参考格式化与高亮规则，确保编辑体验一致性。

### IntelliJ API 兼容与平台升级
- `https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html#intellij-platform-20253`：跟踪平台 API 变更，提前规避兼容风险。

### Sui 生态与 CLI 参考
- `https://github.com/MystenLabs/sui`：对标 Sui CLI 行为与 Move 包结构，确认集成假设。
