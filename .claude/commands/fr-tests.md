---
description: From Design.txt, write a happy-path and a negative test for every FR, through the Orchestrator facade. Run with ./test-fr.sh
argument-hint: [optional: only some FRs, e.g. "FR-3 FR-4", or extra FRs inline]
---
Write functional-requirement tests from `Design.txt`. Scope (optional): $ARGUMENTS

Read `Design.txt` (ignore `#` lines): PACKAGE, the FUNCTIONAL REQUIREMENTS and the ORCHESTRATOR
methods. Keep my FR numbering; don't invent requirements; if an FR is ambiguous, state your reading
in one line.

Write `src/test/java/com/lld/<package>/<Problem>FrTest.java`:
- Class: `@DisplayName("<Problem>: functional requirements")`, `@ExtendWith(RequirementReporter.class)`,
  `@TestMethodOrder(MethodOrderer.MethodName.class)`. Import `com.lld.core.RequirementReporter`.
- `@BeforeEach`: `clock = new MutableClock(Instant.parse("2026-01-01T09:00:00Z"))`,
  `events = new EventBus()` (subscribe a list to the event types you want to assert on), and
  `app = <Problem>Orchestrator.create(clock, events)`. Tests talk ONLY to `app`.
- For EACH FR, in its own `// ---------- FR-n: <summary> ----------` group:
  - `frN_a_<happy behaviour>`: the valid case, asserting the returned value and the observable state
    (getters, a follow-up facade query, published events);
  - `frN_b_<negative behaviour>`: the most important way it must fail, asserting the exact exception:
    `ValidationException` (bad input), `NotFoundException` (unknown id), `ConflictException` (the rule
    rejects it), `InvalidStateTransitionException` (illegal lifecycle move);
  - `frN_c_...` only for a real boundary (limit reached exactly, time just past a cutoff).
  - `@DisplayName("FR-n happy: ...")` / `("FR-n negative: ...")` in plain words.
- Given / when / then separated by blank lines; one behaviour per test; `assertThrows` for failures.
- Deterministic: `clock.advance(...)` for time; no `Thread.sleep`, randomness or threads.
- Don't change main code, except: if an FR needs a facade method that doesn't exist, add the
  signature to the orchestrator and the manager interface with a TODO body, and tell me.

Run `./test-fr.sh`. Right after /scaffold, every test should FAIL with the TODO message; that's
expected (red). Show me the checklist, then a table: FR | happy test | negative test.
