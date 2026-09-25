---
description: HELP, only when asked. Names the core algorithm / structure / pattern you need. /hint, /hint more, /hint code
argument-hint: <what you're stuck on, or a failing test> [more | code]
---
I'm asking for a hint on: $ARGUMENTS

Before answering, read the relevant production code and tests (and `.claude/harness/REPO_NOTES.md`
and `Design.txt` if they exist) so the hint fits THIS codebase, not a
textbook. For "which pattern" questions use `.claude/harness/pattern-roadmap.md`. Use `.claude/harness/hints.md` to map the problem's shape to an approach; if one of the
reference blocks in `.claude/harness/blocks/` fits, name it.

Give ONLY the level asked for:

**Level 1 (default).** The core algorithm, data structure, pattern or concurrency mechanism this
needs, the one property of the problem that points to it, and the classic pitfall. No steps, no
code. At most 5 lines.

**Level 2 (arguments contain "more").** The approach as 4-7 numbered steps, time/space of the key
operation, the edge cases the tests will probably check, and the exact class and method it
belongs in. No code.

**Level 3 (arguments contain "code").** A skeleton of ONLY the core method, at most 25 lines, using
this repo's names and style, with comments where the details go. Show it in chat; do not write any
file unless I say "apply".

If the arguments name a failing test, first say in one line what it expects and why the current
code fails it. Never change files while giving a hint.
