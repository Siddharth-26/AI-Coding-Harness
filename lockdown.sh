#!/usr/bin/env bash
# Fence Claude Code into THIS folder.
#
# Writes .claude/settings.local.json (machine-specific, git-ignored) with deny rules for every
# file and folder in your home directory that is not on the path to this project, so Claude's
# Read/Grep/Glob/Edit tools cannot touch your other projects, even if the harness is cloned
# inside a folder full of other code (e.g. ~/IdeaProjects).
#
#   ./lockdown.sh           # run once after cloning, and again if you move the folder
#   ./lockdown.sh <dir>     # fence a different project (attach.sh does this for a given repo)
#
# ~/.m2 is left out so Maven can still build; the Maven credential files inside it are denied
# by the committed .claude/settings.json. ~/.claude (and any other Claude profile folder) IS denied:
# it holds past session transcripts from other projects. Claude Code still loads its own config;
# these rules only restrict what its tools can open.
set -euo pipefail

cd "${1:-$(dirname "$0")}"
project="$(pwd -P)"
home="$(cd ~ && pwd -P)"

json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

denied=()
if [[ "$project" == "$home"/* ]]; then
  rel="${project#"$home"/}"
  IFS='/' read -ra segments <<< "$rel"
else
  segments=()   # project lives outside home: deny every top-level entry of home
fi

dir="$home"
level=0
while :; do
  keep="${segments[$level]:-}"
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == "$keep" ]] && continue
    [[ "$dir" == "$home" && "$entry" == ".m2" ]] && continue
    denied+=("${dir#"$home"}/$entry")
  done < <(ls -A "$dir")
  [[ -z "$keep" || $level -ge $(( ${#segments[@]} - 1 )) ]] && break
  dir="$dir/$keep"
  level=$((level + 1))
done

mkdir -p .claude
{
  echo '{'
  echo '  "permissions": {'
  echo '    "deny": ['
  first=1
  for path in ${denied[@]+"${denied[@]}"}; do   # safe with set -u on macOS bash 3.2
    p="$(json_escape "$path")"
    for tool in Read Edit; do
      for suffix in "" "/**"; do
        [[ $first -eq 0 ]] && echo ','
        printf '      "%s(~%s%s)"' "$tool" "$p" "$suffix"
        first=0
      done
    done
  done
  echo
  echo '    ]'
  echo '  }'
  echo '}'
} > .claude/settings.local.json

echo "Wrote .claude/settings.local.json: ${#denied[@]} paths outside this project are now off-limits to Claude."
echo "Project: $project"
echo "Verify inside Claude Code with /permissions, then ask it to read a file in another project (it must refuse)."
