---
description: Skeptical-reviewer audit: layer violations, races, SOLID, edge cases, Design.txt coverage. Changes nothing.
argument-hint: [optional: a file or area to focus on]
---
Audit the code in `com.lld.<PACKAGE from Design.txt>` the way a skeptical senior interviewer would.
Focus (optional): $ARGUMENTS
Do NOT modify any files.

Output a punch list, most severe first. Each item:
`file:line: problem — concrete failure scenario — smallest fix`

Check, in this order:
1. **Design.txt coverage**: every FR has code and a passing happy + negative test; every PATTERNS line
   is actually applied (or say which isn't).
2. **Layers** (AGENTS.md): business rules in the orchestrator; a manager calling another manager or
   writing another entity's repository; repositories with logic; `new` of collaborators outside
   `create()`; public setters bypassing the state machine.
3. **Races**: check-then-act on shared state, non-atomic read-modify-write, snapshot lists used
   without re-checking, lock ordering, locks held across slow calls.
4. **SOLID**: switches on type that should be polymorphism/Strategy/Factory (OCP), fat interfaces
   (ISP), dependence on concrete classes (DIP), and for each applied pattern whether the claimed
   extension point really needs no edits.
5. **Edge cases** the FRs imply but no test covers: empty/null input, duplicates, limits reached
   exactly, zero/negative amounts, not found, terminal states.
6. `Instant.now()`, `double` for money, static mutable state.

Finish with the 3 questions an interviewer is most likely to ask about this code, with a one-line
answer for each.
