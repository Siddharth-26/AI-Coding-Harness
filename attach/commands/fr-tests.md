---
description: Write tests proving each functional requirement (FR-n), in this repo's own test style
argument-hint: [FR-1: ...; FR-2: ...]   (no arguments = read Design.txt)
---
Write functional-requirement tests. Requirements: $ARGUMENTS

Follow `.claude/harness/RULES.md`. If no FRs are given, read the FUNCTIONAL REQUIREMENTS section of
`Design.txt` in the repo root (ignore `#` lines). Keep my numbering; don't invent requirements; if one is
ambiguous, state your interpretation in one line.

- Look at their existing tests first and use the same framework, assertion library and style.
  No new dependencies. Don't edit their test classes: create a new class `<Feature>FrTest` in the
  same package as the main class under test.
- For EACH FR: one happy-path test plus the 1-2 key negative or boundary tests.
- Naming: JUnit 5 → `@DisplayName("FR-n: ...")` with method `frN_<behaviour>`; JUnit 4 / TestNG →
  method `frN_<behaviour>`.
- Deterministic: if the code accepts a Clock or time source, use it; never sleep. If an FR can't be
  tested without a time seam, tell me instead of faking it.
- Don't change main code. If an FR isn't implemented yet, still write its test and say it will fail.

Run only this class, using the single-class command in `.claude/harness/REPO_NOTES.md`, and show the
result. For each failure, say whether the test or the code is wrong, and change nothing until I
say so. End with a table: FR | tests | what they prove.
