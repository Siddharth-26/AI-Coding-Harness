---
inclusion: always
---
# Machine-coding harness: session rules

- The project rules are in `AGENTS.md` in the project root (Kiro loads it automatically). If it isn't
  in your context, read it before doing anything else, and follow it for the whole session.
- `Design.txt` in the project root is the design for the current problem. Follow it; never edit it.
- The harness commands are saved prompts in `.kiro/prompts/`, called as `@scaffold`, `@fr-tests`,
  `@nfr-tests`, `@pattern`, `@audit`, `@hld`, `@hint`. When one is used, follow it exactly and treat
  the rest of my message as its arguments.
- Don't suggest the core approach or algorithm unless I use `@hint` or ask directly.
- After changing Java, run `mvn -q compile`; after changing behaviour, run `./test-fr.sh` (and
  `./test-nfr.sh` if NFR tests exist). Report failures verbatim.

Keep `.kiro/steering/` to this one file: Kiro CLI loads every file in it into every session.
