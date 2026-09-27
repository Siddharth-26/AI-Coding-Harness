---
description: From the problem folder's Design.txt, write a test for every behaviour each FR states (returns, throws, changes), in <folder>/test. Ends with how to run them.
argument-hint: <problem folder> [optional: only some FRs, e.g. "FR-3 FR-4"]
---
Write functional-requirement tests. Arguments: $ARGUMENTS

**Folder.** The first argument is the problem folder if it is a folder; otherwise run
`./lld.sh list` and take the only or newest one. Say which in your first line. If it has no
`pom.xml`, run `./lld.sh init <folder>`. Read `<folder>/Design.txt` (any capitalisation; never
edit it) and the code in `<folder>/main/`.

**1. Extract the behaviour.** For each FR (keep my numbering, don't invent FRs), list every
statement it makes that a test can check:
- what a call **returns**, including in the edge case the FR names ("put on a full cache returns
  the evicted key"; "returns empty if absent");
- what it **throws**, and when;
- what **changes** afterwards, observable through the public API (a later get, size, status).
If an FR is ambiguous, state your reading in one line. If a behaviour can't be observed through
the declared API, say so rather than testing private state.

**2. Write `<folder>/test/<pkg path>/<Name>FrTest.java`**, same package as the code (`<Name>` = the
main class or entry point in Design.txt):
- `@DisplayName("<Name>: functional requirements")`, `@ExtendWith(RequirementReporter.class)`,
  `@TestMethodOrder(MethodOrderer.MethodName.class)`; `import com.lld.core.RequirementReporter;`.
- Per FR, a `// ---------- FR-n: <summary> ----------` group with one test per behaviour statement:
  - `frN_a_<behaviour>`: the happy case. Assert the exact returned value (not just "not null") and
    the state afterwards through the public API.
  - `frN_b_<behaviour>`: the most important way it must fail or the "nothing happens" case, with
    `assertThrows(<exact exception>)` or the exact empty result. Exceptions: the ones Design.txt
    names, else the standard ones in AGENTS.md.
  - `frN_c_...`: the boundary the FR implies (exactly at capacity, one past a limit, updating an
    existing key when full).
  - `@DisplayName("FR-n happy: ...")`, `("FR-n negative: ...")`, `("FR-n boundary: ...")`, in plain
    words that say the expected result ("put on a full cache returns the evicted key 'b'").
- Build objects with the constructors and methods Design.txt declares. Given / when / then
  separated by blank lines; one behaviour per test; deterministic (no sleep, threads or randomness).
- Test code only. If a class or method the tests need doesn't exist in `main/` yet, create it
  exactly as Design.txt declares with a `TODO FR-n` body (as /scaffold does) and say so. If an FR
  needs a return value or parameter the declared signature lacks, follow the DRIFT rule in
  AGENTS.md. Otherwise don't touch main code.

**3. Run** `./lld.sh test <folder> fr`. Right after /scaffold every test should FAIL with its TODO
message: that's the expected red.

**Reply with:**
1. the checklist the run printed;
2. a table: FR | behaviour statement | test method, so a missing behaviour is visible;
3. **How to run**: paste the output of `./lld.sh howto <folder>`.
