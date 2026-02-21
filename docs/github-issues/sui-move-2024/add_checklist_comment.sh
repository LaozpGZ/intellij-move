#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   bash docs/github-issues/sui-move-2024/add_checklist_comment.sh 12
#   bash docs/github-issues/sui-move-2024/add_checklist_comment.sh 12 13 14
#   GH_REPO=LaozpGZ/intellij-move bash docs/github-issues/sui-move-2024/add_checklist_comment.sh 1-8
#
# Notes:
# - Accepts issue numbers and ranges (e.g. 1-8)
# - Skips issues that already contain the checklist marker
# - Use --force to append again even if marker exists

MARKER="## Checklist Template (Execution Tracking)"
FORCE=false

if [[ "${1:-}" == "--force" ]]; then
  FORCE=true
  shift
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 [--force] <issue-number|range> [more-issues...]"
  echo "Example: $0 1 3 8"
  echo "Example: $0 1-8"
  exit 1
fi

REPO="${GH_REPO:-}"
if [[ -z "${REPO}" ]]; then
  REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner')
fi

expand_issue_arg() {
  local token="$1"
  if [[ "$token" =~ ^[0-9]+-[0-9]+$ ]]; then
    local start="${token%-*}"
    local end="${token#*-}"
    if (( start > end )); then
      echo "Invalid range: $token" >&2
      exit 1
    fi
    seq "$start" "$end"
  elif [[ "$token" =~ ^[0-9]+$ ]]; then
    echo "$token"
  else
    echo "Invalid issue id/range: $token" >&2
    exit 1
  fi
}

read -r -d '' TEMPLATE <<'TPL' || true
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
TPL

TMP_FILE=$(mktemp)
printf "%s\n" "$TEMPLATE" > "$TMP_FILE"
trap 'rm -f "$TMP_FILE"' EXIT

# Build unique issue list preserving first occurrence order
ISSUES=()
for arg in "$@"; do
  while IFS= read -r n; do
    already=false
    for e in "${ISSUES[@]:-}"; do
      if [[ "$e" == "$n" ]]; then
        already=true
        break
      fi
    done
    if [[ "$already" == false ]]; then
      ISSUES+=("$n")
    fi
  done < <(expand_issue_arg "$arg")
done

echo "Repo: $REPO"
for issue in "${ISSUES[@]}"; do
  existing=$(gh issue view "$issue" -R "$REPO" --json comments -q '.comments[].body' || true)
  if [[ "$FORCE" == false ]] && printf "%s" "$existing" | rg -Fq "$MARKER"; then
    echo "#$issue: checklist exists, skipped"
    continue
  fi

  gh issue comment "$issue" -R "$REPO" --body-file "$TMP_FILE" >/dev/null
  echo "#$issue: checklist comment added"
done
