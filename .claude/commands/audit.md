---
description: Skeptical-reviewer audit of the problem folder: Design.txt conformance and drift, FR behaviour coverage, races, SOLID, edge cases. Changes nothing.
argument-hint: <problem folder> [optional: a file or area to focus on]
---
Audit the problem folder the way a skeptical senior interviewer would. Arguments: $ARGUMENTS
Do NOT modify any files.

**Folder.** The first argument is the problem folder if it is a folder; otherwise run
`./lld.sh list` and take the only or newest one. Say which in your first line. Read
`<folder>/Design.txt`, everything in `<folder>/main/` and `<folder>/test/`, and the output of
`./lld.sh drift <folder>`.

Output a punch list, most severe first. Each item:
`file:line: problem — concrete failure scenario — smallest fix`

Check, in this order:
1. **Design.txt conformance**: every declared class and signature exists exactly as declared, or
   carries a DRIFT comment that names an FR, an NFR or a requested pattern; unmarked drift;
   classes Design.txt doesn't name; classes that only forward calls to another (duplicate layers).
2. **FR behaviour**: every behaviour statement in each FR (returns, throws, changes) has code AND
   an asserting test. Name the statement that has neither, e.g. "FR-3: put on a full cache must
   return the evicted key; put returns Optional.empty() and no test checks it".
3. **Races** (if any NFR mentions threads): check-then-act on shared state, non-atomic
   read-modify-write, two structures updated without one lock, lock ordering, locks held across
   slow calls.
4. **SOLID**: switches on type that should be polymorphism or a strategy (OCP), fat interfaces
   (ISP), dependence on concrete classes (DIP), and for each applied pattern whether the claimed
   extension point really needs no edits.
5. **Edge cases** the FRs imply but no test covers: empty or null input, duplicates, limit reached
   exactly, zero or negative values, not found, terminal states.
6. `Instant.now()`, `double` for money, static mutable state.

Finish with:
- the drift summary: each DRIFT line and whether its reason holds up;
- the 3 questions an interviewer is most likely to ask about this code, with a one-line answer each.
