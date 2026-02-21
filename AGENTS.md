# Repository Guidelines

## 当前任务目标（2026 H1）
- 本文件与 `docs/2026-1_todo.md` 保持一致。
- 主目标：
  - JetBrains 兼容基线覆盖 `2025.3 (253.*)`，并完成 `2026.1 EAP (261.*)` 预验证。
  - `261` 预验证已恢复全链路（不跳过 `generateLexer/generateParser`）。
  - 完成 Move 2024 edition 在 parser/lexer/highlighting/completion/inspection 的完整支持。
- 发布前必须通过：
  - `./gradlew compileKotlin`
  - `./gradlew test`
  - `./gradlew verifyPlugin`

## Project Structure & Module Organization
- Core plugin code lives in `src/main/kotlin/org/sui` (primary Kotlin sources) and `src/main/java/org/sui` (small Java interoperability layer).
- Grammar definitions are under `src/main/grammars` (`MoveLexer.flex`, `MoveParser.bnf`); generated parser/PSI files are written to `src/main/gen`.
- Tests live in `src/test/kotlin` with fixtures and sample projects in `src/test/resources`.
- Plugin metadata and IDE resources are in `src/main/resources` (`META-INF/plugin.xml`, inspections, intentions, icons, templates).
- Additional notes and architecture docs are in `docs/` and `CLAUDE.md`.

## Build, Test, and Development Commands
- `./gradlew compileKotlin` — compile Kotlin/Java sources (also triggers lexer/parser generation).
- `./gradlew test` — run automated tests in `src/test`.
- `./gradlew runIde` — launch a sandbox IntelliJ instance with the plugin.
- `./gradlew buildPlugin` — build distributable plugin ZIP.
- `./gradlew verifyPlugin` — run IntelliJ Plugin Verifier checks (same as CI workflow).
- `./gradlew generateLexer generateParser` — regenerate grammar artifacts after editing `.flex`/`.bnf`.

## Coding Style & Naming Conventions
- Use 4-space indentation; keep lines and functions focused (KISS/YAGNI).
- Follow Kotlin/Java defaults: `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants, lowercase package names.
- Keep features modular by domain (`org.sui.lang`, `org.sui.ide`, `org.sui.cli`, `org.sui.toml`).
- Do not manually edit generated files in `src/main/gen`; change grammar definitions and regenerate.

## Testing Guidelines
- Test stack: JUnit 4 + IntelliJ Platform test framework.
- Name test files `*Test.kt`; keep one behavior focus per test class.
- Prefer targeted runs during development, e.g. `./gradlew test --tests "org.sui.ide.annotator.errors.MatchArmDuplicateErrorTest"`.
- Add regression tests alongside bug fixes, especially for parser, annotator, and inspection changes.

## Commit & Pull Request Guidelines
- Prefer Conventional Commit prefixes seen in history: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- Keep commit messages imperative and scoped (example: `fix: deduplicate import candidates`).
- PRs should include: change summary, rationale, affected modules, and test evidence (`./gradlew test` output).
- For UI-facing changes (tool window, inspections, formatting), include screenshots or before/after snippets.

## IntelliJ API Compatibility & Platform Upgrades
- Track official API changes for 2025.3 at `https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html#intellij-platform-20253` before upgrading platform targets.
- When changing `shortPlatformVersion`, `platformVersion`, or related Gradle properties, validate migration impact with focused tests and plugin verifier runs.
