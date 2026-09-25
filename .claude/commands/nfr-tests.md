---
description: From Design.txt, write one check per NFR (race, idempotency, latency, throughput, fault, time), through the facade. Run with ./test-nfr.sh
argument-hint: [optional: only some NFRs, or extra NFRs inline]
---
Write non-functional-requirement tests from `Design.txt`. Scope (optional): $ARGUMENTS

Read `Design.txt` (ignore `#` lines): PACKAGE, NON-FUNCTIONAL REQUIREMENTS, ORCHESTRATOR. If an NFR
has no number (count, time, rate), ask me for one instead of guessing.

Map each NFR to exactly one check:
| NFR kind | Test |
|---|---|
| consistency / no double-X under concurrency | `ConcurrentRunner.run(N, i -> app...)`: assert the exact number of winners, the losers' exception type, and an invariant afterwards; loop 20 rounds with a fresh `app` |
| idempotency / safe retries | the same request sequentially, then concurrently: one side effect, the same result for every caller |
| latency | `Perf.latency(warmup, iterations, i -> app...)`: assert p99 < 10x the target, stated in the display name ("asserted at 50 ms") |
| throughput | `Perf.throughput(threads, opsPerThread, i -> app...)`: assert >= target / 10, stated in the display name |
| fault tolerance | build the orchestrator with its constructor and a fake dependency that throws `TransientException` k times (or always): assert recovery, or a clean state after failure |
| time-based (TTL, expiry, SLA) | `clock.advance` to just before / at / just after the boundary |
| durability, multi-region, scaling, network security | not testable in-process: list them in the class Javadoc under "Covered in HLD"; no fake test |

Write `src/test/java/com/lld/<package>/<Problem>NfrTest.java` with `@DisplayName("<Problem>:
non-functional requirements")`, `@ExtendWith(RequirementReporter.class)`,
`@TestMethodOrder(MethodOrderer.MethodName.class)`, a class Javadoc with the budget rule and the
"Covered in HLD" list, and tests named `nfrN_<property>` with `@DisplayName("NFR-n: ...")`. Call
`RequirementReporter.note(...)` with the measured value (stats, rate, rounds x threads). Use unique
ids per operation so operations only collide when the test wants them to. Don't change main code.

Run `./test-nfr.sh`, show the checklist with the numbers, and for each FAIL explain what it says
about the design (race, O(n) path, missing retry...) and the smallest fix, without applying it.
