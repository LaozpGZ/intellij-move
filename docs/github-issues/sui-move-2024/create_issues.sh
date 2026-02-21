#!/usr/bin/env bash
set -euo pipefail

# 说明：
# 1) 先执行 gh auth login
# 2) 在目标仓库根目录执行本脚本
# 3) 如需里程碑，请先手工在 GitHub 创建后加上 --milestone

ISSUE_DIR="docs/github-issues/sui-move-2024"

create_issue() {
  local title="$1"
  local labels="$2"
  local body_file="$3"

  echo "Creating issue: ${title}"
  gh issue create \
    --title "${title}" \
    --label "${labels}" \
    --body-file "${body_file}"
}

create_issue \
  "feat(sui-move): close positional fields semantic loop" \
  "sui-move,move-2024,p1,parser,resolve,types" \
  "${ISSUE_DIR}/01-positional-fields.md"

create_issue \
  "feat(sui-move): support break value expressions for Move 2024" \
  "sui-move,move-2024,p1,parser,types,annotator" \
  "${ISSUE_DIR}/02-break-value-expressions.md"

create_issue \
  "feat(sui-move): add revised path style support for global ::" \
  "sui-move,move-2024,p1,parser,resolve,completion" \
  "${ISSUE_DIR}/03-revised-paths-global-prefix.md"

create_issue \
  "feat(annotator): make match diagnostics guard-aware" \
  "sui-move,move-2024,p1,annotator,types" \
  "${ISSUE_DIR}/04-match-guard-aware-diagnostics.md"

create_issue \
  "feat(types): align equality auto-referencing with Move 2024" \
  "sui-move,move-2024,p2,types,inspection" \
  "${ISSUE_DIR}/05-equality-auto-referencing.md"

create_issue \
  "test(resolve/imports): lock nested use and std default-use behavior" \
  "sui-move,move-2024,p2,resolve,imports,completion,tests" \
  "${ISSUE_DIR}/06-nested-use-and-std-default-use.md"

create_issue \
  "test: convert legacy TODO tests into executable regressions" \
  "sui-move,move-2024,p2,tests" \
  "${ISSUE_DIR}/07-legacy-todo-test-debt.md"

create_issue \
  "chore(verification): harden verifyPlugin compatibility gate" \
  "sui-move,move-2024,p2,build,verification" \
  "${ISSUE_DIR}/08-verifyplugin-hardening.md"

echo "All 8 issues created."
