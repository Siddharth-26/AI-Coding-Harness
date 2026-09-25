@AGENTS.md

## Claude Code specifics

- Commands in `.claude/commands/`, in the order you'll use them:
  `/scaffold` → `/fr-tests` → (you prompt the implementation) → `/pattern` → `/nfr-tests` →
  `/audit` → `/hld`, plus `/hint` for help on the approach, only when invoked.
- After writing Java, run `mvn -q compile`, and `./test-fr.sh` / `./test-nfr.sh` when behaviour
  changed. Report failures verbatim instead of guessing.
- Keep diffs small and inside `com.lld.<package>` from Design.txt.
