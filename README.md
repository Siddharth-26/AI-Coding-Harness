# Machine-coding harness (core Java 17)

Problem-agnostic. Nothing in here is tied to a specific interview question. You write the design;
the harness turns it into layered code, a test for every requirement, and the patterns you choose.

## The loop

| # | You | Harness |
|---|---|---|
| 1 | Fill `Design.txt` top to bottom: PROBLEM, PACKAGE, FRs, NFRs, ENTITIES, REPOSITORIES, MANAGERS, ORCHESTRATOR, PATTERNS | |
| 2 | Review the generated layers | `/scaffold`: model, repository (interface + in-memory), manager (interface + Default impl with TODO bodies), the Orchestrator facade with `create(clock, events)`, Demo. Compiles. |
| 3 | Show the red checklist | `/fr-tests`: a happy and a negative test per FR, through the facade. `./test-fr.sh` → all FAIL |
| 4 | **Prompt the implementation yourself**, one manager method at a time | `./test-fr.sh` → green |
| 5 | Make it extensible, one PATTERNS line at a time | `/pattern Strategy — fee calc in OrderManager — rules vary by distance` → built into the right layer, tests stay green, extension point named |
| 6 | Prove the NFRs | `/nfr-tests` → `./test-nfr.sh` prints measured numbers |
| 7 | Pre-empt the critique, then zoom out | `/audit`, then `/hld <scale numbers>` |

Stuck on the approach? `/hint <what>` (then `more`, then `code`). The agent never suggests the core
approach unless you ask.

## What's here

```
Design.txt              your design; every command and script reads it
AGENTS.md               rules for the agent: layers, conventions, concurrency, tests
CLAUDE.md               Claude Code: loads AGENTS.md
.kiro/steering/         Kiro: harness.md only (always loaded); keep it the only file there
.kiro/prompts/          Kiro CLI: the commands as @scaffold @fr-tests @nfr-tests @pattern @audit @hld @hint
.claude/commands/       Claude Code: the same commands as /scaffold /fr-tests ... (the source of truth)
kiro-sync.sh            regenerates .kiro/prompts from .claude/commands after you edit a command
docs/pattern-roadmap.md 20 patterns in the order you'd introduce them: signal, layer, template,
                        extension point, and the /pattern line to type
docs/hints.md           problem shape -> core algorithm (used by /hint)
hld/TEMPLATE.md         HLD skeleton with Mermaid
src/main/java/com/lld/core/   generic building blocks only (no problem code):
    Repository, InMemoryRepository, IdGenerator, exceptions, EventBus, KeyedLockManager,
    MutableClock, SingleFlight, IdempotencyStore, ExpiringHolds, RetryPolicy, realtime/
src/test/java/com/lld/core/   ConcurrentRunner, Perf, RequirementReporter (+ the harness's own
                              *SelfCheck tests, which never run in a normal test run)
```
Your problem's code goes in `src/main/java/com/lld/<PACKAGE>/` and its tests in
`src/test/java/com/lld/<PACKAGE>/`. Nothing else runs when you test.

## Layers (what /scaffold builds and /audit checks)

`model/` (entities, value records, enums with transition tables) → `repository/`
(`XRepository` interface + `InMemoryXRepository`) → `manager/` (`XManager` interface +
`DefaultXManager`: the business rules) → `<Problem>Orchestrator` (the facade: the only entry
point, delegates to managers, and `create()` wires everything). Patterns go in packages named after
what varies (`pricing/`, `cancellation/`, `notification/`).

## Scripts

| Script | Does |
|---|---|
| `./test-fr.sh` / `./test-nfr.sh` | Run your FR / NFR tests (package from Design.txt) and print the PASS/FAIL checklist |
| `./run.sh` | Run your Demo |
| `./selfcheck.sh` | Run the harness's own 35 checks once after setup |
| `./lockdown.sh` | Block Claude from reading anything in your home folder outside this project |
| `./claude-personal.sh [repo]` | Start Claude Code with a separate personal profile (work Mac) |
| `./attach.sh <repo>` / `./detach.sh <repo>` | Bring the commands (Kiro CLI `@` and Claude `/`) into a repo they give you, and remove them again |
| `./kiro-sync.sh` | Regenerate `.kiro/prompts/` after editing a command |

## Using it with Kiro CLI

```bash
cd ~/ai-coding-practice
kiro-cli                 # start chat in the project folder
```
- **Loaded automatically in every session:** `AGENTS.md` (project root) and `.kiro/steering/harness.md`.
  Check once with `/context show`, and by asking "What layers and naming does this project use?".
  It should answer model / repository / manager / Orchestrator and `Default<X>Manager`.
- **Commands are saved prompts**, called with `@` plus your arguments on the same line:
  `@scaffold`, `@fr-tests`, `@nfr-tests`, `@audit`, `@hld`, `@hint <what>`, and
  `@pattern Strategy — fee calc in OrderManager — rules vary by distance`.
  If the arguments don't come through (older CLI versions need the whole line in single quotes),
  inject the file instead: `@.kiro/prompts/pattern.md Strategy — fee calc — rules vary`. That always works.
- **Keep `.kiro/steering/` to the one file.** Kiro CLI ignores inclusion modes and loads every file in
  that folder into every session, which is why the commands live in `.kiro/prompts/`.
- The first time the agent runs `mvn` or a test script, approve it and trust that tool for the session
  so you're not prompted every time. Still read every file change before accepting it: that review is
  what the interviewer wants to see.
- `lockdown.sh`, `claude-personal.sh` and `.claude/settings.json` only affect Claude Code.
- Edited a command in `.claude/commands/`? Run `./kiro-sync.sh` so `.kiro/prompts/` matches.
- Kiro **IDE** instead of the CLI: the IDE supports on-demand steering (`#name`), the CLI doesn't; ask
  me if you switch and I'll generate that variant.

## One-time setup

```bash
mkdir -p ~/ai-coding-practice && tar -xzf ~/Downloads/lld-harness.tar.gz -C ~/ai-coding-practice --strip-components=1
cd ~/ai-coding-practice
./selfcheck.sh              # 35 checks pass (first run downloads JUnit)
mvn -o -q compile           # wifi off: proves nothing needs the network
./lockdown.sh
./claude-personal.sh        # /login with your personal account; /status to confirm
```
Open the folder in IntelliJ, and run `./claude-personal.sh` from its terminal. Close other projects;
the IDE plugin shares whatever file you have open.

## When they give you a repo

```bash
git clone <their repo> clones/<name>
./attach.sh clones/<name>
./claude-personal.sh clones/<name>
```
In their repo: `/onboard` (maps build, tests, stubs, failures), fill the `Design.txt` attach put in
the root, then `/fr-tests`, `/next`, `/pattern`, `/nfr-tests`, `/audit`, `/hld`, and `/hint` when
stuck. Their conventions win and their tests are never edited. Everything attach adds is
git-excluded; run `./detach.sh clones/<name>` before you zip or hand it back.

## Practice routine

Pick any problem (not from a list), give yourself 90 minutes for steps 1-7, then read what
`/audit` found that you missed. Do a fresh `Design.txt` each time; move the old package out
(`git stash -u`, or commit it to a practice branch).
