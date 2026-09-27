---
description: Skeptical-reviewer audit of MY changes to this repo (SOLID, concurrency, edge cases, rule breaks). Changes nothing.
argument-hint: [file or package to focus on]
---
Audit my work in this repository. Focus (optional): $ARGUMENTS
Do NOT modify any files.

Scope: `git diff` against the commit I started from (`git log --reverse --format=%H | head -1`
if unsure), plus the code my changes call into.

Output a punch list, most severe first. Each item:
`file:line: problem — concrete failure scenario — smallest fix`

Check, in this order:
1. Breaks of `.claude/harness/RULES.md`: edited or skipped provided tests, renamed or reshaped a
   public API, new dependency, style that doesn't match the repo; code that differs from my
   Design.txt without a `// DRIFT:` line; an FR behaviour (returns, throws, changes) with no
   asserting test.
2. Race conditions: check-then-act on shared state, non-atomic read-modify-write, missing locks
   around multi-step updates, lock ordering.
3. SOLID: type switches that should be polymorphism or a strategy, services doing entity work,
   dependence on concrete classes.
4. Edge cases the problem statement implies but no visible test covers: empty and null input,
   duplicates, boundaries, not found. Hidden tests usually live here.
5. `double` for money, `Instant.now()` instead of an injected clock, static mutable state.

Finish with the 3 questions an interviewer is most likely to ask about my changes, with a
one-line answer for each.
