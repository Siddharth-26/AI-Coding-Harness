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
If `Design.txt` exists in the repo root, it is my design for this problem (FRs, NFRs, entities,
interfaces, patterns). It is local and git-excluded. Follow it, but never edit it.

## Commands
`/onboard` map the repo · `/next` explain the next failing test · `/fr-tests` `/nfr-tests` tests
from Design.txt · `/pattern` apply a pattern · `/hint` help on the approach (only when asked) ·
`/audit` review my changes · `/hld` design doc
