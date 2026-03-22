#!/usr/bin/env sh
ROOT='C:\Users\lweis\Documents\newsthread'
REMOTE='origin'
STATE_REF='refs/holistic/state'
LEGACY_SEED_REF='refs/heads/holistic/state'
BRANCH=$(git -c core.hooksPath=/dev/null -C "$ROOT" rev-parse --abbrev-ref HEAD) || exit 1
git -c core.hooksPath=/dev/null -C "$ROOT" push "$REMOTE" "$BRANCH" || exit 1
TMPDIR=$(mktemp -d 2>/dev/null || mktemp -d -t holistic-state)
git -c core.hooksPath=/dev/null -C "$ROOT" worktree add --force "$TMPDIR" >/dev/null 2>&1 || exit 1
cleanup() { git -c core.hooksPath=/dev/null -C "$ROOT" worktree remove --force "$TMPDIR" >/dev/null 2>&1; }
trap cleanup EXIT
cd "$TMPDIR" || exit 1
REMOTE_STATE_EXISTS=false
REMOTE_LEGACY_EXISTS=false
if git -c core.hooksPath=/dev/null ls-remote --quiet --exit-code "$REMOTE" "$STATE_REF" >/dev/null 2>&1; then
  REMOTE_STATE_EXISTS=true
fi
if [ "$REMOTE_STATE_EXISTS" != "true" ] && [ -n "$LEGACY_SEED_REF" ]; then
  if git -c core.hooksPath=/dev/null ls-remote --quiet --exit-code "$REMOTE" "$LEGACY_SEED_REF" >/dev/null 2>&1; then
    REMOTE_LEGACY_EXISTS=true
  fi
fi
if [ "$REMOTE_STATE_EXISTS" = "true" ]; then
  git -c core.hooksPath=/dev/null fetch --quiet "$REMOTE" "$STATE_REF" >/dev/null 2>&1 || exit 1
  git -c core.hooksPath=/dev/null switch --detach FETCH_HEAD >/dev/null 2>&1 || exit 1
elif [ "$REMOTE_LEGACY_EXISTS" = "true" ]; then
  git -c core.hooksPath=/dev/null fetch --quiet "$REMOTE" "$LEGACY_SEED_REF" >/dev/null 2>&1 || exit 1
  git -c core.hooksPath=/dev/null switch --detach FETCH_HEAD >/dev/null 2>&1 || exit 1
else
  git -c core.hooksPath=/dev/null switch --orphan "holistic-sync-tmp" >/dev/null 2>&1 || exit 1
fi
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp "$ROOT/HOLISTIC.md" "$TMPDIR/HOLISTIC.md"
cp "$ROOT/AGENTS.md" "$TMPDIR/AGENTS.md"
cp "$ROOT/HISTORY.md" "$TMPDIR/HISTORY.md"
cp "$ROOT/CLAUDE.md" "$TMPDIR/CLAUDE.md"
cp "$ROOT/GEMINI.md" "$TMPDIR/GEMINI.md"
cp -R "$ROOT/.holistic" "$TMPDIR/.holistic"
git -c core.hooksPath=/dev/null add "HOLISTIC.md" "AGENTS.md" "HISTORY.md" "CLAUDE.md" "GEMINI.md" ".holistic"
git -c core.hooksPath=/dev/null diff --cached --quiet || git -c core.hooksPath=/dev/null commit -m 'chore(holistic): sync portable state' >/dev/null 2>&1
git -c core.hooksPath=/dev/null push "$REMOTE" HEAD:"$STATE_REF"
