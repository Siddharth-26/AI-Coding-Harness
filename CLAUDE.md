@AGENTS.md

## Claude Code specifics

- Commands in `.claude/commands/`, in the order you'll use them, each taking the problem folder
  first: `/scaffold <folder>` → `/fr-tests <folder>` → (you prompt the implementation) →
  `/pattern <folder> ...` → `/nfr-tests <folder>` → `/audit <folder>` → `/hld <folder> ...`,
  plus `/hint` for help on the approach, only when invoked.
- Build and test through `./lld.sh` (compile, test <folder> fr|nfr, drift, howto). Report
  failures verbatim instead of guessing.
- Write only inside the problem folder. Design.txt is read-only.
