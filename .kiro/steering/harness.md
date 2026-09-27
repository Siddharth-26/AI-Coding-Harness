---
inclusion: always
---
# Machine-coding harness: session rules

- The project rules are in `AGENTS.md` in the project root (Kiro loads it automatically). If it isn't
  in your context, read it before doing anything else, and follow it for the whole session.
- I work in a problem folder: `<folder>/Design.txt`, code in `<folder>/main/`, tests in
  `<folder>/test/`. Write only inside that folder. `./lld.sh list` shows the folders.
- `Design.txt` is mine: never edit, rewrite or restate it. Its signatures are a contract; any
  change you make for an FR, an NFR or a pattern I asked for gets a
  `// DRIFT: ... because FR-n | NFR-n | pattern Name` comment inside the method. Build only the classes it names: no default managers, orchestrators or facades.
- The harness commands are saved prompts in `.kiro/prompts/`, called as `@scaffold`, `@fr-tests`,
  `@nfr-tests`, `@pattern`, `@audit`, `@hld`, `@hint`, with the problem folder as the first
  argument. When one is used, follow it exactly and treat the rest of my message as its arguments.
- Don't suggest the core approach or algorithm unless I use `@hint` or ask directly.
- After changing Java: `./lld.sh compile <folder>`; after changing behaviour:
  `./lld.sh test <folder> fr` (and `nfr` if NFR tests exist). Report failures verbatim.

Keep `.kiro/steering/` to this one file: Kiro CLI loads every file in it into every session.
