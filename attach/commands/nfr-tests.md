---
description: Write tests checking each non-functional requirement (NFR-n), in this repo's own test style
argument-hint: [NFR-1: ...; NFR-2: ...]   (no arguments = read Design.txt)
---
Write non-functional-requirement tests. Requirements: $ARGUMENTS

Follow `.claude/harness/RULES.md`. If no NFRs are given, read the NON-FUNCTIONAL REQUIREMENTS section of
`Design.txt` in the repo root (ignore `#` lines). If an NFR has no number (count, time, rate), ask me for one.

Use the repo's own test framework and no new dependencies. Put the helpers you need as private
static methods inside the new test class `<Feature>NfrTest` (same package as the class under test):
- concurrency: an `ExecutorService` + `CountDownLatch` start gate that fires N tasks at once and
  collects each result or exception;
- latency: warm up, then time N calls with `System.nanoTime()` and compute p50/p99.

Map each NFR to exactly one kind of check:
| NFR says | Test |
|---|---|
| consistent / never double-booked under concurrency | N threads at once; assert the exact number of winners, the losers' exception, and an invariant afterwards; repeat 20 rounds with fresh objects |
| idempotent / safe to retry | same request sequentially and concurrently → one side effect, same result |
| latency | p99 below 10x the target (stated in the test name) |
| throughput | ops/s at least target / 10 (stated in the test name) |
| fault tolerance | fake the external dependency to fail k times or permanently; assert recovery or a clean state |
| time-based (TTL, expiry) | use the code's clock seam at just before / at / just after the boundary; if there is no seam, say so |
| durability, multi-region, scaling, network security | not testable here: list them in the class Javadoc under "Covered in HLD" and write no fake test |

Naming as in /fr-tests but with `NFR-n` / `nfrN_`. Don't change main code. Run only this class with
the single-class command from `.claude/harness/REPO_NOTES.md`, and show the measured numbers. For
each failure, explain what it means about the design and propose the smallest fix without applying it.
