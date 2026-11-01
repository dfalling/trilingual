---
description: Create a pull request — runs precommit, commits any changes, detects issue numbers in branch names, and enables automerge by default.
allowed-tools: Bash, Read, Edit, Write, Glob, Grep
---

## Context

- Current branch: !`git branch --show-current`
- Git status: !`git status --short`
- Git diff (staged + unstaged): !`git diff HEAD`
- Recent commits: !`git log --oneline -5`

## Your task

Arguments (optional PR title or extra instructions): $ARGUMENTS

Follow these steps in order:

### Step 1 — Run pre-PR checks and fix issues

Run the same checks CI enforces and fix any errors or warnings before proceeding. Repeat until it passes cleanly:

`./gradlew lintDebug testDebugUnitTest assembleDebug`

### Step 2 — Commit any uncommitted changes

If there are staged or unstaged changes after precommit passes:
1. Stage all changed tracked files: `git add -u`
2. Draft a concise commit message that describes the changes (focus on "why", not "what")
3. Create the commit

### Step 3 — Push the branch

Push the current branch to origin:
```
git push -u origin HEAD
```

### Step 4 — Determine PR description

Check the current branch name for a GitHub issue number pattern (e.g. `123-fix-something`, `feature/456-add-widget`, `fix-789`).

- If an issue number is found, append `Closes #<number>` to the PR body.
- If no issue number is found, omit the closing reference.

### Step 5 — Create the pull request

Use `gh pr create` with:
- Title: derived from $ARGUMENTS if provided, otherwise infer from the commit messages on the branch vs main
- Body: brief summary of changes, plus `Closes #<number>` if applicable (use a HEREDOC to pass the body)
- Base branch: `main`

Example:
```bash
gh pr create --base main --title "..." --body "$(cat <<'EOF'
## Summary
- <bullet points>

Closes #<number>
EOF
)"
```

### Step 6 — Enable automerge

Unless $ARGUMENTS contains "no-automerge" or "no automerge", enable automerge after the PR is created:
```bash
gh pr merge --auto --squash
```

If the repo does not support automerge (command fails), note this to the user but do not treat it as a fatal error.
