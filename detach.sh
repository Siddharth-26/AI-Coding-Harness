#!/usr/bin/env bash
# Remove everything attach.sh added to a repo, leaving it exactly as you cloned it plus your own
# code changes.
#   ./detach.sh ~/ai-coding-practice/clones/<name>
set -euo pipefail

target_arg="${1:?usage: ./detach.sh <path to the repo you attached>}"
[[ -d "$target_arg" ]] || { echo "Not a directory: $target_arg" >&2; exit 1; }
target="$(cd "$target_arg" && pwd -P)"
manifest="$target/.claude/harness/ATTACHED"
[[ -f "$manifest" ]] || { echo "Nothing to detach: $manifest not found." >&2; exit 1; }

removed=0
while IFS= read -r rel; do
  [[ -z "$rel" || "$rel" == /* || "$rel" == *..* ]] && continue   # only plain repo-relative paths
  if [[ -e "$target/$rel" ]]; then
    rm -f "$target/$rel"
    removed=$((removed + 1))
  fi
done < "$manifest"

rm -rf "$target/.claude/harness"
rmdir "$target/.claude/commands" 2> /dev/null || true   # only if now empty
rmdir "$target/.claude" 2> /dev/null || true
rmdir "$target/.kiro/steering" 2> /dev/null || true
rmdir "$target/.kiro/prompts" 2> /dev/null || true
rmdir "$target/.kiro" 2> /dev/null || true

if git -C "$target" rev-parse --git-dir > /dev/null 2>&1; then
  exclude="$(git -C "$target" rev-parse --git-path info/exclude)"
  [[ "$exclude" == /* ]] || exclude="$target/$exclude"
  if [[ -f "$exclude" ]]; then
    sed -i.bak '/^# >>> lld-harness/,/^# <<< lld-harness/d' "$exclude" && rm -f "$exclude.bak"
  fi
fi

echo "Detached: removed $removed files and .claude/harness/ from $target"
while IFS= read -r design; do
  echo "  kept your ${design#"$target"/} (no longer git-excluded): delete it before you zip or push if it shouldn't go with the repo"
done < <(find "$target" -name .git -prune -o -type f -iname 'design.txt' -print 2> /dev/null)
