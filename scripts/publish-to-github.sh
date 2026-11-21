#!/usr/bin/env bash
# Creates a private GitHub repository from this folder and pushes it.
# Pushing to main starts the "Build APK" workflow automatically.
#
# Requirements: git and the GitHub CLI (gh). Usage: ./scripts/publish-to-github.sh [repo-name]
set -euo pipefail

REPO_NAME="${1:-codex-terminal}"

command -v git >/dev/null 2>&1 || { echo "git is required."; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "GitHub CLI (gh) is required: https://cli.github.com"; exit 1; }

if ! gh auth status >/dev/null 2>&1; then
  gh auth login
fi

cd "$(dirname "$0")/.."

if [ ! -d .git ]; then
  git init -b main
fi

git add .
git commit -m "Codex Terminal Android app" || echo "Nothing new to commit."

gh repo create "$REPO_NAME" --private --source=. --remote=origin --push

echo
echo "Done. Build progress: https://github.com/$(gh api user -q .login)/$REPO_NAME/actions"
