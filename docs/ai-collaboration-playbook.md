# AI 协作落地手册（可复用）

> 版本：v1.0  
> 目标：把“需求 → 可执行任务 → GitHub 跟踪 → 验收闭环”标准化，供团队长期复用。

---

## 1. 本次对话沉淀出的可复用方法

这次我们完成了一个完整闭环，关键做法可复制到后续任何特性推进：

1. **先做差距盘点（Gap Analysis）**：按 `P0/P1/P2` 分层，不一上来就写代码。
2. **先文档化再执行**：先产出“剩余缺口清单 + 测试矩阵”。
3. **Issue 模板化**：一次性产出 8 条可执行 Issue 模板（含 DoD）。
4. **批量自动化落库**：脚本化创建 Issues、指派负责人、挂 Milestone、设置 due date。
5. **统一质量结构**：所有 Issue 正文统一 `Context / Scope / Acceptance / Background`。
6. **执行跟踪模板化**：每条 Issue 自动补 Checklist 评论，支持推进中打勾。
7. **异常有兜底流程**：登录失败、仓库设置、标签缺失、网络抖动都有标准处理。

---

## 2. 标准协作流程（SOP）

## Phase A：需求澄清与边界锁定

- 明确目标：例如“可实用 + 新特性可靠支持”。
- 锁定范围：例如“仅 Sui Move，不含 Aptos”。
- 产出：1 页范围说明（In Scope / Out of Scope）。

## Phase B：差距盘点与优先级

- 输出 `P0/P1/P2` 缺口清单。
- 每条缺口给出：现状证据、风险、建议改动层级（parser/resolve/types/inspection/build）。
- 产出：`remaining-gaps-matrix.md`。

## Phase C：任务化（Issue 设计）

- 将每个 gap 转成 1 条 Issue。
- 每条 Issue 必须有：`Goal / Tasks / DoD / Suggested Tests`。
- 产出：`issue-templates.md` + 独立 `issue body` 文件。

## Phase D：GitHub 批量落地

- 确保仓库启用 Issues。
- 创建/校验标签（如 `p1/p2`, `parser`, `types` 等）。
- 批量创建 Issue。
- 统一指派 Owner。
- 按 `p1/p2` 挂载到 `P1/P2` Milestone。
- 设置里程碑截止时间和描述。

## Phase E：执行规范化

- Issue 正文统一前缀：`Context / Scope / Acceptance / Background`。
- 追加 Checklist 评论模板，用于推进与验收记录。

## Phase F：验收与收口

- 定向回归（按 issue 范围）。
- 全量回归：`./gradlew test --no-daemon`。
- 门禁回归（如涉及）：`./gradlew verifyPlugin --no-daemon`。
- 在 Issue Checklist 勾选并留“Update Log”。

---

## 3. Issue 正文统一模板（英文）

```md
## Context
This issue belongs to the Sui Move 2024 execution board. Keep implementation aligned with the milestone objective and issue intent.

## Scope
- <2~3 domain-specific scope bullets>

## Acceptance
- <2~3 measurable acceptance bullets>

## Background
<problem statement and rationale>

## Goal
<goal>

## Tasks
- [ ] ...

## DoD
- [ ] ...

## Suggested tests
- ...
```

---

## 4. Checklist 评论模板（英文）

```md
## Checklist Template (Execution Tracking)

- [ ] **Implementation**: code changes are completed and scoped to this issue.
- [ ] **Targeted Tests**: targeted tests were executed; include commands and short results.
- [ ] **Full Regression**: `./gradlew test --no-daemon` was executed and result recorded.
- [ ] **Verification (if applicable)**: `./gradlew verifyPlugin --no-daemon` was executed and result recorded.
- [ ] **Risk Notes**: key risks/edge cases and mitigations are documented.
- [ ] **Artifacts**: related PR/commit links and report paths are attached.

**Update Log**
- Date:
- Owner:
- Notes:
```

---

## 5. 本仓库已落地的可复用资产

- 缺口矩阵：`docs/sui-move-2024-remaining-gaps-matrix.md`
- Issue 模板总表：`docs/sui-move-2024-issue-templates.md`
- Issue body 目录：`docs/github-issues/sui-move-2024/`
- 批量建单脚本：`docs/github-issues/sui-move-2024/create_issues.sh`
- Checklist 批量评论脚本：`docs/github-issues/sui-move-2024/add_checklist_comment.sh`
- 快速命令说明：`docs/github-issues/sui-move-2024/checklist-snippet.md`

---

## 6. 常见故障处理（Runbook）

## 6.1 `gh auth` 问题

- 现象：`You are not logged into any GitHub hosts`。
- 处理：
  1. 本机先执行 `gh auth login -h github.com -p https`
  2. 验证 `gh auth status -h github.com`

## 6.2 仓库禁用 Issues

- 现象：`repository has disabled issues`。
- 处理：`gh repo edit <owner/repo> --enable-issues`

## 6.3 标签不存在

- 现象：`could not add label: 'xxx' not found`。
- 处理：先 `gh label create ...` 再建单。

## 6.4 API 404 / EOF

- 先确认 `-R owner/repo` 是否正确。
- 再检查权限与网络抖动，必要时重试并做幂等（先查后改）。

---

## 7. 团队执行规则（建议）

- 一个 Issue 只做一个目标（KISS）。
- 非本 Issue 变更不混入（YAGNI）。
- 重复流程脚本化（DRY）。
- 每条 Issue 都有可量化 DoD（可验收）。
- 每次推进必须更新 Checklist + Update Log。

---

## 8. 最小执行命令集（可直接复制）

```bash
# 1) 批量创建 Issue
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/create_issues.sh"

# 2) 给新 Issue 添加 Checklist 评论
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" 9

# 3) 批量范围添加 Checklist
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" 1-8
```

---

## 9. Definition of Done（流程级）

当满足以下条件，可判定本轮协作流程完成：

- [ ] Gap 清单 + 测试矩阵已成文。
- [ ] Issues 已创建、指派、挂 Milestone、设截止日期。
- [ ] Issues 正文结构统一且为英文可执行条目。
- [ ] 每条 Issue 已有 Checklist 评论模板。
- [ ] 定向回归 + 全量门禁命令有记录。
- [ ] 风险与后续动作可追踪。


---

## 10. Wiki 首页同步（可选）

当仓库启用 Wiki 后，可用以下命令把协作手册入口同步到 Wiki `Home.md`：

```bash
GH_REPO="LaozpGZ/intellij-move" bash "docs/sync-wiki-home.sh"
```

> 若首次执行提示 Wiki 仓库不可用，请先在网页打开并初始化一次：
> `https://github.com/LaozpGZ/intellij-move/wiki`
