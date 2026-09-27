# Working in a repo the interviewer gave me

This repository belongs to the interviewer: their code, their conventions, their tests. I (the
candidate) own every decision; you are a fast, precise pair. These files under `.claude/` are my
local tooling. They are git-excluded and are not part of the solution.

## Hard rules
- Never edit, delete, skip (`@Disabled`, `@Ignore`) or weaken the tests or test data they gave
  me. If one of their tests looks wrong, say so and stop.
- Don't rename or reshape existing public classes, methods, packages or file layout. Visible and
  hidden tests depend on them. Add code; don't restructure.
- Match what's there: package layout, naming, code style, exceptions, logging, Java version,
  build tool, test framework. No new dependencies.
- Don't copy anything from `.claude/harness/blocks/` into the repo unless I ask. When I do,
  adapt package, names and style to this repo.
- Don't suggest the core algorithm or approach unless I run `/hint` or ask directly. Implement
  the approach I describe; if it has a correctness bug (not a style preference), flag it in one
  line.
- Read before writing: the file you're changing, its callers, and the tests that exercise it.
- Make the smallest change that turns the next failing test green, then run the tests with the
  command recorded in `.claude/harness/REPO_NOTES.md` and report the result.
- Never commit, push, or change git config unless I ask.

## How to behave
- Code first, prose last; at most 3 bullets of assumptions.
- The visible tests are a floor, not the whole spec: also handle what the problem statement
  implies (null/empty input, duplicates, boundaries, concurrency if it is mentioned). Ask before
  adding behaviour the statement doesn't imply.
- If `.claude/harness/REPO_NOTES.md` doesn't exist yet, suggest running `/onboard` first.

## Design.txt
My `Design.txt` (any capitalisation, wherever I put it; I'll name the path if there are several)
is my design for this problem: FRs, NFRs, classes, signatures, patterns. It is local and
git-excluded (attach.sh excludes the name).
- Never edit, rewrite or restate it.
- Its signatures are a contract for the code I add, except where they clash with this repo's
  existing public API: their API and their tests win, and you tell me about the clash.
- Every behaviour an FR states (what is returned, thrown, changed) is implemented and tested.
- You may improve on it when an FR or NFR needs it, but every change from what it says is
  written into the code as the first line of each affected method (or above the changed field or
  added class):
  `// DRIFT: <what Design.txt says, or "not in Design.txt"> -> <what the code does>, because <FR-n | NFR-n | pattern Name>: <why>`
  Replies that change code end with the DRIFT lines added (or "none"). `grep -rn "DRIFT:" .`
  lists them all.

## Commands
`/onboard` map the repo · `/next` explain the next failing test · `/fr-tests` `/nfr-tests` tests
from Design.txt · `/pattern` apply a pattern · `/hint` help on the approach (only when asked) ·
`/audit` review my changes · `/hld` design doc
