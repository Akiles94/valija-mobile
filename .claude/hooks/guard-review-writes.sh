#!/usr/bin/env bash
# guard-review-writes.sh
# PreToolUse hook for the change-reviewer subagent (matcher: Write).
# The reviewer must be structurally unable to modify the code it is judging.
# Edit is already denied in frontmatter; this blocks Write to anything except
# the review.md verdict file. Exit code 2 blocks the tool call.

set -euo pipefail

INPUT=$(cat)

# Extract tool_input.file_path. Prefer `node` (always present in this Node project);
# fall back to `jq`. If neither exists, fail CLOSED so the guard can't silently
# fail open. (jq is not installed on this machine.)
if command -v node >/dev/null 2>&1; then
  FILE_PATH=$(printf '%s' "$INPUT" | node -e 'try{const j=JSON.parse(require("fs").readFileSync(0,"utf8"));process.stdout.write((j.tool_input&&j.tool_input.file_path)||"")}catch{}')
elif command -v jq >/dev/null 2>&1; then
  FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
else
  echo "Blocked: guard-review-writes.sh needs node or jq to parse the tool payload, but neither is on PATH." >&2
  exit 2
fi

# No path to inspect — nothing to block.
if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# Allow only the review artifact; block writes to any source or config file.
case "$FILE_PATH" in
  */review.md|review.md)
    exit 0
    ;;
  *)
    echo "Blocked: change-reviewer may only write review.md, not $FILE_PATH." >&2
    exit 2
    ;;
esac
