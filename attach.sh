#!/usr/bin/env bash
# Bring the harness's AI tooling into a repo someone else gives you (company boilerplate with
# their own code and tests), WITHOUT changing their code or their git history.
#
#   git clone <their repo> ~/ai-coding-practice/clones/<name>
#   ./attach.sh ~/ai-coding-practice/clones/<name>
#   ./claude-personal.sh ~/ai-coding-practice/clones/<name>     # then type /onboard
#   ...
#   ./detach.sh ~/ai-coding-practice/clones/<name>              # before you zip or hand it back
#
# What it adds to the repo (every path is listed in .claude/harness/ATTACHED and in the repo's
# .git/info/exclude, so `git status` stays clean and none of it can be committed or pushed):
#   .claude/commands/  onboard, next, hint, fr-tests, nfr-tests, pattern, audit, hld (Claude Code: /name)
#   .kiro/prompts/     the same commands for Kiro CLI (@name); a prompt the repo already has is left alone
#   .kiro/steering/harness-rules.md   the RULES.md text, always loaded by Kiro
#   .claude/harness/   RULES.md, hints.md, pattern-roadmap.md, the HLD template, an example
#                      Design.txt, and the harness building blocks as read-only reference (not compiled)
#   (your own Design.txt, wherever you write it, is git-excluded by name too)
#   CLAUDE.md, AGENTS.md    only if the repo has none (they load RULES.md)
#   .claude/settings.json   only if the repo has none (its build commands pre-approved + deny rules)
#   .claude/settings.local.json   via ./lockdown.sh: Claude can't read outside this repo
set -euo pipefail

harness="$(cd "$(dirname "$0")" && pwd -P)"
target_arg="${1:?usage: ./attach.sh <path to the repo you were given>}"
[[ -d "$target_arg" ]] || { echo "Not a directory: $target_arg" >&2; exit 1; }
target="$(cd "$target_arg" && pwd -P)"
[[ "$target" != "$harness" ]] || { echo "That's the harness itself. Point attach.sh at the repo you were given." >&2; exit 1; }

hdir="$target/.claude/harness"
manifest="$hdir/ATTACHED"
if [[ -f "$manifest" ]]; then
  echo "Already attached. Run ./detach.sh \"$target\" first if you want to attach again." >&2
  exit 1
fi
if [[ -e "$target/.claude/settings.local.json" ]]; then
  echo "The repo already has .claude/settings.local.json; not overwriting it. Move it away and re-run." >&2
  exit 1
fi

added=()        # repo-relative paths we created (outside .claude/harness/)
skipped=()

mkdir -p "$target/.claude/commands" "$hdir"

# ---------- commands ----------
for src in "$harness"/attach/commands/*.md; do
  name="$(basename "$src")"
  if [[ -e "$target/.claude/commands/$name" ]]; then
    skipped+=(".claude/commands/$name (the repo already has one)")
  else
    cp "$src" "$target/.claude/commands/$name"
    added+=(".claude/commands/$name")
  fi
done

# ---------- Kiro CLI: commands as saved prompts (@onboard, @pattern ...) + always-on rules ----------
# (Kiro CLI loads every file in .kiro/steering/, so only the rules go there; commands go in .kiro/prompts/)
mkdir -p "$target/.kiro/prompts" "$target/.kiro/steering"
for src in "$harness"/attach/kiro-prompts/*.md; do
  name="$(basename "$src")"
  if [[ -e "$target/.kiro/prompts/$name" ]]; then
    skipped+=(".kiro/prompts/$name (the repo already has one)")
  else
    cp "$src" "$target/.kiro/prompts/$name"
    added+=(".kiro/prompts/$name")
  fi
done
if [[ -e "$target/.kiro/steering/harness-rules.md" ]]; then
  skipped+=(".kiro/steering/harness-rules.md (the repo already has one)")
else
  {
    echo "---"
    echo "inclusion: always"
    echo "---"
    echo "<!-- Added by lld-harness attach.sh (git-excluded). Commands: @onboard @next @fr-tests @nfr-tests @pattern @audit @hld @hint -->"
    echo
    sed -E 's@`/(onboard|next|hint|fr-tests|nfr-tests|pattern|audit|hld)`@`\@\1`@g' "$harness/attach/RULES.md"
  } > "$target/.kiro/steering/harness-rules.md"
  added+=(".kiro/steering/harness-rules.md")
fi

# ---------- knowledge + reference blocks (read-only, never compiled) ----------
cp "$harness/attach/RULES.md"          "$hdir/RULES.md"
cp "$harness/docs/hints.md"            "$hdir/hints.md"
cp "$harness/docs/pattern-roadmap.md"  "$hdir/pattern-roadmap.md"
cp "$harness/hld/TEMPLATE.md"          "$hdir/HLD-TEMPLATE.md"
mkdir -p "$hdir/blocks"
cp -R "$harness/src/main/java/com/lld/core/." "$hdir/blocks/"

# ---------- an example Design.txt (you write your own, anywhere in the repo) ----------
cp "$harness/templates/Design.example.txt" "$hdir/Design.example.txt"

# ---------- instructions for the agent ----------
if [[ -e "$target/CLAUDE.md" ]]; then
  skipped+=("CLAUDE.md (the repo has its own; the commands load .claude/harness/RULES.md themselves)")
else
  {
    echo "<!-- Local AI instructions added by lld-harness attach.sh. Git-excluded; not part of this repo. -->"
    echo "@.claude/harness/RULES.md"
  } > "$target/CLAUDE.md"
  added+=("CLAUDE.md")
fi
if [[ -e "$target/AGENTS.md" ]]; then
  skipped+=("AGENTS.md (the repo has its own)")
else
  cp "$harness/attach/RULES.md" "$target/AGENTS.md"
  added+=("AGENTS.md")
fi

# ---------- build detection + permissions ----------
build="none found"
allow=()
if [[ -f "$target/pom.xml" ]]; then
  mvn="mvn"; [[ -x "$target/mvnw" ]] && mvn="./mvnw"
  build="Maven ($mvn)"
  allow=("Bash($mvn -q compile)" "Bash($mvn -q test-compile)" "Bash($mvn -q test)" "Bash($mvn test)")
elif [[ -f "$target/build.gradle" || -f "$target/build.gradle.kts" ]]; then
  gradle="gradle"; [[ -x "$target/gradlew" ]] && gradle="./gradlew"
  build="Gradle ($gradle)"
  allow=("Bash($gradle compileJava)" "Bash($gradle compileTestJava)" "Bash($gradle test)" "Bash($gradle build)")
fi

tests="unknown"
if grep -rqs "org.junit.jupiter" "$target" --include=*.java --include=pom.xml --include=*.gradle --include=*.kts; then
  tests="JUnit 5"
elif grep -rqs "org.junit.Test" "$target" --include=*.java; then
  tests="JUnit 4"
elif grep -rqs "org.testng" "$target" --include=*.java --include=pom.xml --include=*.gradle --include=*.kts; then
  tests="TestNG"
fi

if [[ -e "$target/.claude/settings.json" ]]; then
  skipped+=(".claude/settings.json (the repo has its own; lockdown rules still apply via settings.local.json)")
else
  {
    echo '{'
    echo '  "permissions": {'
    echo '    "allow": ['
    first=1
    for rule in ${allow[@]+"${allow[@]}"}; do
      [[ $first -eq 0 ]] && echo ','
      printf '      "%s"' "$rule"
      first=0
    done
    echo
    echo '    ],'
    echo '    "deny": ['
    echo '      "Read(~/.ssh/**)", "Read(~/.aws/**)", "Read(~/.m2/settings.xml)",'
    echo '      "Read(~/.m2/settings-security.xml)", "Read(~/.gradle/gradle.properties)", "Read(//Volumes/**)",'
    echo '      "Bash(curl:*)", "Bash(wget:*)", "Bash(ssh:*)", "Bash(scp:*)",'
    echo '      "Bash(git push:*)", "Bash(git commit:*)", "Bash(rm -rf:*)"'
    echo '    ]'
    echo '  }'
    echo '}'
  } > "$target/.claude/settings.json"
  added+=(".claude/settings.json")
fi

"$harness/lockdown.sh" "$target" > /dev/null
added+=(".claude/settings.local.json")

# ---------- manifest + git exclude ----------
for path in "${added[@]}"; do
  echo "$path"
done > "$manifest"

exclude_note="not a git repo: nothing to exclude; run ./detach.sh before you zip it"
if git -C "$target" rev-parse --git-dir > /dev/null 2>&1; then
  exclude="$(git -C "$target" rev-parse --git-path info/exclude)"
  [[ "$exclude" == /* ]] || exclude="$target/$exclude"
  mkdir -p "$(dirname "$exclude")"
  {
    echo "# >>> lld-harness (added by attach.sh, removed by detach.sh)"
    echo "/.claude/harness/"
    echo "[Dd]esign.txt"
    echo "DESIGN.txt"
    for path in "${added[@]}"; do
      echo "/$path"
    done
    echo "# <<< lld-harness"
  } >> "$exclude"
  exclude_note="git-excluded via $(basename "$(dirname "$(dirname "$exclude")")")/info/exclude; git status stays clean"
fi

# ---------- summary ----------
echo "Attached the harness to: $target"
echo "  build: $build    tests: $tests"
echo "  added: ${#added[@]} files + .claude/harness/   ($exclude_note)"
for s in ${skipped[@]+"${skipped[@]}"}; do
  echo "  skipped: $s"
done
echo
echo "Next:"
echo "  Write your Design.txt (anywhere in the repo; example: .claude/harness/Design.example.txt)"
echo "  Kiro CLI:    cd \"$target\" && kiro-cli, then @onboard, @fr-tests, @next,"
echo "               @pattern, @nfr-tests, @audit, @hld (@hint when stuck)"
echo "  Claude Code: $harness/claude-personal.sh \"$target\", then the same commands with /"
echo "  Before you zip or hand the repo back: $harness/detach.sh \"$target\""
