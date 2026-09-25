---
description: Apply ONE design pattern where you say, for the reason you give, inside THIS repo's structure. Keeps their API and tests green.
argument-hint: <Pattern> — <where: class/method/area> — <intuition: what varies, reacts or validates>
---
Apply this pattern: $ARGUMENTS

Follow `.claude/harness/RULES.md` (their conventions win; never touch their tests or public API).

1. Read the card for this pattern in `.claude/harness/pattern-roadmap.md`, the code at the place I
   named, and `Design.txt` if it exists (its PATTERNS section, when I only give a pattern name).
2. Fit check, one line only: if the intuition doesn't match the pattern's signal, say so and name the
   better-fitting pattern, then do what I asked unless I stop you.
3. Implement it the way the card shows, but in THIS repo's package layout, naming and style:
   domain-named interface, the varying or reacting behaviour moved behind it, injected through the
   constructor, and the default wired wherever this repo builds its objects. Add at least two
   implementations when the pattern is about variation.
4. Don't change existing public signatures or any provided test. Compile and run the tests with the
   commands in `.claude/harness/REPO_NOTES.md`. They must be as green as before.

Report in at most 8 lines: files added and changed; the extension point ("to add <X>, write a class
implementing <Y> and register it in <Z>"); the principle it serves; the question an interviewer is
most likely to ask about it, with a one-line answer.
