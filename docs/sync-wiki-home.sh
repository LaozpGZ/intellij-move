#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   GH_REPO="LaozpGZ/intellij-move" bash docs/sync-wiki-home.sh

REPO="${GH_REPO:-}"
if [[ -z "$REPO" ]]; then
  REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner')
fi

PLAYBOOK_URL="https://github.com/${REPO}/blob/master/docs/ai-collaboration-playbook.md"
WIKI_URL="https://github.com/${REPO}.wiki.git"
TMP_DIR=$(mktemp -d /tmp/wiki-sync.XXXXXX)

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "Repo: $REPO"
echo "Wiki URL: $WIKI_URL"

if ! git clone "$WIKI_URL" "$TMP_DIR"; then
  echo ""
  echo "[ERROR] Wiki git repository is not available yet."
  echo "Please open this page once and create the first wiki page manually:"
  echo "  https://github.com/${REPO}/wiki"
  echo "Then run this script again."
  exit 1
fi

cd "$TMP_DIR"

if [[ ! -f Home.md ]]; then
  cat > Home.md <<EOF2
# IntelliJ Move Wiki

## Team Collaboration Playbook

For our reusable AI-assisted collaboration workflow (SOP, issue templates, scripts, and execution checklist), see:

- [AI Collaboration Playbook]($PLAYBOOK_URL)
EOF2
else
  if ! rg -q "AI Collaboration Playbook|Team Collaboration Playbook" Home.md; then
    cat >> Home.md <<EOF2

## Team Collaboration Playbook

For our reusable AI-assisted collaboration workflow (SOP, issue templates, scripts, and execution checklist), see:

- [AI Collaboration Playbook]($PLAYBOOK_URL)
EOF2
  fi
fi

if git diff --quiet; then
  echo "No changes detected. Wiki Home is already up to date."
  exit 0
fi

if [[ -z "$(git config --get user.name || true)" ]]; then
  git config user.name "$(gh api user --jq .login)"
fi
if [[ -z "$(git config --get user.email || true)" ]]; then
  login="$(gh api user --jq .login)"
  git config user.email "${login}@users.noreply.github.com"
fi

git add Home.md
git commit -m "docs: add collaboration playbook entry to wiki home"
git push origin HEAD

echo "Wiki home updated: https://github.com/${REPO}/wiki"
