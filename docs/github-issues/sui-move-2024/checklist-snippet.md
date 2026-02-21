# Reusable `gh` Snippet: Add Checklist Comment

## One-liner (single issue)

```bash
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" 9
```

## Batch (multiple issues)

```bash
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" 9 10 11
```

## Range (e.g. 1-8)

```bash
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" 1-8
```

## Force add even if marker already exists

```bash
GH_REPO="LaozpGZ/intellij-move" bash "docs/github-issues/sui-move-2024/add_checklist_comment.sh" --force 1-8
```

## Behavior

- Uses marker: `## Checklist Template (Execution Tracking)`
- Skips duplicate insertion by default
- Supports issue numbers and numeric ranges
