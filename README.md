# Machine-coding harness (core Java 17)

Problem-agnostic. You write the design in your own problem folder; the harness builds exactly the
classes and signatures you wrote, writes tests for every behaviour your FRs state, and tells you how
to run them. It never edits your Design.txt and never writes outside your folder.

## The loop

| # | You | Harness (Kiro CLI `@name`, Claude Code `/name`) |
|---|---|---|
| 1 | Make a folder and write `Design.txt` in it, in your own format: FRs, NFRs, classes and signatures, patterns | |
| 2 | Set it up | `./lld.sh init lrucache` → `pom.xml`, `main/`, `test/` (Design.txt untouched) |
| 3 | Review the skeleton | `@scaffold lrucache` → exactly your classes and signatures in `main/`, TODO bodies, compiles |
| 4 | Show the red checklist | `@fr-tests lrucache` → a test per behaviour each FR states, in `test/`; ends with how to run them |
| 5 | **Prompt the implementation yourself** | `./lld.sh test lrucache fr` → green |
| 6 | Make it extensible, one pattern at a time | `@pattern lrucache Strategy — eviction in LRUCache — LRU today, LFU later` |
| 7 | Prove the NFRs | `@nfr-tests lrucache` → `./lld.sh test lrucache nfr` prints measured numbers |
| 8 | Pre-empt the critique, then zoom out | `@audit lrucache`, `./lld.sh drift lrucache`, `@hld lrucache <scale numbers>` |

Stuck on the approach? `@hint lrucache <what>` (then `more`, then `code`). The agent never suggests
the core approach unless you ask.

## Your problem folder

```
lrucache/                  anywhere: next to lld.sh (simplest) or inside src/main/java
  Design.txt               yours, any format, any capitalisation. Nothing edits it.
  pom.xml                  from ./lld.sh init: the folder is its own small Maven project
  main/lrucache/...        the code (package = folder name, unless Design.txt names one)
  test/lrucache/...        LRUCacheFrTest, LRUCacheNfrTest (same package as the code)
  test/com/lld/core/...    test kit: RequirementReporter (the PASS/FAIL checklist), ConcurrentRunner, Perf, MutableClock
```
`templates/Design.example.txt` is an LRU example of a Design.txt the harness reads well. The things
that matter: numbered FRs that say exactly what is returned / thrown / changed, NFRs with numbers,
and your method signatures.

## The rules the agent follows (AGENTS.md)

- **Design.txt is read-only.** Never edited, rewritten or restated.
- **Your signatures are a contract.** Classes, methods, parameter and return types exactly as written.
- **Only what you named.** No default manager / orchestrator / facade / repository layers, and no
  class that only forwards calls to another.
- **Improvements are allowed but visible.** If an FR or NFR needs a change (a lock for thread
  safety, `ConcurrentHashMap.compute`, a different return type), the agent makes it and writes a
  comment inside every affected method:
  ```java
  // DRIFT: not in Design.txt -> check, evict and insert under the cache lock, because NFR-1: two puts must not both see room
  ```
  `./lld.sh drift lrucache` lists every one, so you can explain each change.
- **Behaviour comes from the FRs.** "put on a full cache returns the evicted key" becomes code that
  returns it and a test that asserts `Optional.of("a")`.
- **Tests live in your folder** and every test command ends with how to run them.

## lld.sh

| Command | Does |
|---|---|
| `./lld.sh init <folder>` | `pom.xml`, `main/`, `test/`, test kit. Never touches Design.txt or existing files |
| `./lld.sh test <folder> fr` / `nfr` / (nothing = all) | Run the FR / NFR / all tests and print the checklist |
| `./lld.sh test <folder> 'LRUCacheFrTest#fr3_a_putOnFullCacheReturnsEvictedKey'` | One test (quote it: zsh treats `#` specially) |
| `./lld.sh howto <folder>` | The exact commands to run this folder's tests (also from inside it, plain Maven, IntelliJ) |
| `./lld.sh drift <folder>` | Every DRIFT comment: where the code differs from Design.txt |
| `./lld.sh compile <folder>` / `run <folder> [MainClass]` | Compile / run the class with `main()` |
| `./lld.sh core <folder>` | Copy the building blocks (KeyedLockManager, ExpiringHolds, IdempotencyStore, SingleFlight, RetryPolicy, EventBus, realtime/) into the folder, only when an NFR needs one |
| `./lld.sh list` | Your problem folders, newest first |

Leave out `<folder>` when you're inside it (`../lld.sh test fr`). From the harness root without a
folder, the newest Design.txt is used and named. Without the script: `cd lrucache && mvn -q test
-Dtest='*FrTest'`. In IntelliJ: right-click `lrucache/pom.xml` → Add as Maven Project (once), then
the green run arrows. Offline: `MAVEN_ARGS=-o ./lld.sh test lrucache fr`.

## What's here

```
AGENTS.md               rules for the agent (read automatically by Kiro CLI; CLAUDE.md loads it)
CLAUDE.md               Claude Code: loads AGENTS.md
.kiro/steering/         harness.md only (always loaded); keep it the only file there
.kiro/prompts/          Kiro CLI commands: @scaffold @fr-tests @nfr-tests @pattern @audit @hld @hint
.claude/commands/       the same commands for Claude Code (the source; ./kiro-sync.sh copies them to .kiro/prompts)
lld.sh                  set up, build, test one problem folder
templates/              problem-pom.xml (used by init), Design.example.txt
docs/pattern-roadmap.md 20 patterns in the order you'd introduce them, with the @pattern line to type
docs/hints.md           problem shape -> core algorithm (used by @hint)
hld/TEMPLATE.md         HLD skeleton with Mermaid
src/main/java/com/lld/core/   building blocks (copied into a folder only by ./lld.sh core)
src/test/java/com/lld/core/   the test kit + the harness's own *SelfCheck tests (./selfcheck.sh)
attach.sh / detach.sh   bring the commands into a repo they give you, and remove them again
lockdown.sh, claude-personal.sh   Claude Code only: keep it inside this project / personal profile
```
The root `pom.xml` builds only `com/lld/core`, so a problem folder inside `src/main/java` never
breaks the Maven build. IntelliJ doesn't know that: if your folder is inside `src/main/java`,
right-click its `pom.xml` → Add as Maven Project straight away, or keep problem folders next to
`lld.sh` (the simplest).

## Using it with Kiro CLI

```bash
cd <harness folder>
kiro-cli
```
- Loaded in every session: `AGENTS.md` and `.kiro/steering/harness.md`. Check once by asking "Where
  do tests go and what is a DRIFT comment?". It should say `<folder>/test/<pkg>` and the DRIFT rule.
- Commands: `@scaffold lrucache`, `@fr-tests lrucache`, `@pattern lrucache Strategy — ... — ...`.
  If the arguments don't come through, inject the file instead:
  `@.kiro/prompts/fr-tests.md lrucache`. That always works.
- The first time the agent runs `./lld.sh`, approve it and trust it for the session. Still read every
  file change before accepting it; that review is what the interviewer wants to see.
- Edited a command in `.claude/commands/`? Run `./kiro-sync.sh`.

## Updating your GitHub copy

Unpack over your clone (it replaces files, it doesn't delete), remove the four files this version
dropped, and commit:
```bash
cd <your clone of AI-Coding-Harness>
tar -xzf ~/Downloads/lld-harness.tar.gz --strip-components=1
git rm -q --ignore-unmatch Design.txt test-fr.sh test-nfr.sh run.sh
chmod +x lld.sh
git add -A && git commit -m "Problem-folder model: read-only Design.txt, DRIFT comments, tests in the folder" && git push
./selfcheck.sh
```

## When they give you a repo

```bash
git clone <their repo> clones/<name>
./attach.sh clones/<name>
```
In their repo: `@onboard`, write your Design.txt anywhere in it (git-excluded by name), then
`@fr-tests`, `@next`, `@pattern`, `@nfr-tests`, `@audit`, `@hld`, and `@hint` when stuck. Their
conventions and tests win; the DRIFT rule applies to your Design.txt. Run `./detach.sh clones/<name>`
before you zip or hand it back.

## Practice routine

Pick any problem (not from a list): new folder, new Design.txt, 90 minutes for steps 1-8, then read
what `@audit` found that you missed. Old folders stay as they are; `./lld.sh list` shows them.
