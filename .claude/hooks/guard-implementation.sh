#!/usr/bin/env bash
# guard-implementation.sh
# PreToolUse hook (matcher: Edit|Write|MultiEdit) for the MAIN session.
# Blocks edits to implementation code while an advance's plan is awaiting approval.
# The gate lifts when the current advance's plan.md carries an "Approved:" line —
# mirroring how guard-git-ops.sh gates push/merge on "Verdict: PASS". Exit code 2
# blocks the tool call and feeds the message back to the agent.

set -euo pipefail

INPUT=$(cat)

# Extract tool_input.file_path. This is a Node project, so `node` is the reliable
# parser; fall back to `jq` if present. If neither exists we cannot tell what is
# being edited, so fail CLOSED (block) with a clear message rather than silently
# letting implementation edits through.
if command -v node >/dev/null 2>&1; then
  FILE_PATH=$(printf '%s' "$INPUT" | node -e 'try{const j=JSON.parse(require("fs").readFileSync(0,"utf8"));process.stdout.write((j.tool_input&&j.tool_input.file_path)||"")}catch{}')
elif command -v jq >/dev/null 2>&1; then
  FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
else
  echo "Blocked: guard-implementation.sh needs node or jq to parse the tool payload, but neither is on PATH. Install one, or the implementation gate cannot run." >&2
  exit 2
fi

# No path to inspect — nothing to gate.
if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# Normalize Windows backslashes so matching is slash-agnostic.
NORM=$(printf '%s' "$FILE_PATH" | tr '\\' '/')

# Gate implementation code only. Everything else (advances/**, docs, specs,
# .claude/**, *.md — including plan.md and the approval marker itself) passes.
case "$NORM" in
  */src/*|src/*|*/package.json|package.json|*/tsup.config.ts|*/tsconfig*.json)
    ;; # implementation file — check approval below
  *)
    exit 0 ;;
esac

# Identify the current advance: an explicit id from the environment
# (e.g. `export VALIJA_ADVANCE=M2`) wins; otherwise the most recently
# modified plan.md under advances/.
if [ -n "${VALIJA_ADVANCE:-}" ]; then
  PLAN="advances/${VALIJA_ADVANCE}/plan.md"
else
  PLAN=$(ls -t advances/*/plan.md 2>/dev/null | head -n1 || true)
fi

# No active plan → no advance in flight → don't obstruct general work.
if [ -z "${PLAN:-}" ] || [ ! -f "$PLAN" ]; then
  exit 0
fi

# Plan present but not yet approved → block implementation.
if ! grep -qiE '^approved:' "$PLAN"; then
  echo "Blocked: $PLAN has no 'Approved:' line. Present the plan to Oscar and get explicit approval before editing implementation code. Add 'Approved: <name> <date>' to plan.md once approved." >&2
  exit 2
fi

exit 0
