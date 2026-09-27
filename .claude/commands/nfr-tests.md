---
description: From the problem folder's Design.txt, write one check per NFR (race, idempotency, latency, throughput, fault, time) in <folder>/test. Ends with how to run them.
argument-hint: <problem folder> [optional: only some NFRs, e.g. "NFR-1"]
---
Write non-functional-requirement tests. Arguments: $ARGUMENTS

**Folder.** The first argument is the problem folder if it is a folder; otherwise run
`./lld.sh list` and take the only or newest one. Say which in your first line. If it has no
`pom.xml`, run `./lld.sh init <folder>`. Read `<folder>/Design.txt` (never edit it) and the code in
`<folder>/main/`. If an NFR has no number (threads, count, time, rate), ask me for one instead
of guessing.

Map each NFR to exactly one check, through the public API Design.txt declares:
| NFR kind | Test |
|---|---|
| thread safety / consistency / no double-X | `ConcurrentRunner.run(N, i -> ...)`: assert the exact number of winners, the losers' exception type, and the invariant afterwards (size never above capacity, no lost update); repeat 20 rounds with a fresh object |
| idempotency / safe retries | the same request sequentially, then concurrently: one side effect, the same result for every caller |
| latency / O(1) | `Perf.latency(warmup, iterations, i -> ...)` at the size the NFR states: assert p99 < 10x the target, stated in the display name ("asserted at 500 us") |
| throughput | `Perf.throughput(threads, opsPerThread, i -> ...)`: assert >= target / 10, stated in the display name |
| fault tolerance | only if the code takes the dependency through its constructor: a fake that fails k times (or always); assert recovery or a clean state |
| time-based (TTL, expiry, SLA) | only if the code takes a `Clock`: move it to just before / at / just after the boundary; otherwise say the design needs a clock |
| durability, multi-region, scaling, network security | not testable in-process: list under "Covered in HLD" in the class Javadoc; no fake test |

Write `<folder>/test/<pkg path>/<Name>NfrTest.java`, same package as the code, with
`@DisplayName("<Name>: non-functional requirements")`, `@ExtendWith(RequirementReporter.class)`,
`@TestMethodOrder(MethodOrderer.MethodName.class)`, imports from `com.lld.core` (RequirementReporter,
ConcurrentRunner, Perf), a class Javadoc with the budget rule and the "Covered in HLD" list, and
tests named `nfrN_<property>` with `@DisplayName("NFR-n: ...")`. Call `RequirementReporter.note(...)`
with the measured value (p50/p99, ops/s, rounds x threads). Use distinct keys or ids per operation
so operations only collide when the test wants them to. Don't change main code.

Run `./lld.sh test <folder> nfr`.

**Reply with:**
1. the checklist with the measured numbers;
2. for each FAIL: what it says about the design (race, O(n) path, missing lock) and the smallest
   fix, without applying it;
3. **How to run**: paste the output of `./lld.sh howto <folder>`.
