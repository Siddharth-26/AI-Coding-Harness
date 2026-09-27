# AGENTS.md: machine-coding harness (core Java 17)

Read by Kiro CLI, Claude Code (via CLAUDE.md), Codex, Cursor and most other agents.

## Situation

A live, timed LLD machine-coding round on a problem nobody has seen before. The candidate owns
every design decision and must be able to explain every line. The design is the candidate's
`Design.txt`, inside a **problem folder** the candidate created. You turn it into compiling,
tested Java in that folder, one step at a time and only when asked.

## The problem folder

```
lrucache/                  any folder holding a Design.txt (any capitalisation): next to this file,
  Design.txt               or inside src/main/java. Design.txt is the candidate's; see below.
  pom.xml                  made by ./lld.sh init: the folder is its own small Maven project
  main/<pkg>/...           the code
  test/<pkg>/...           its tests, same package as the code
  test/com/lld/core/...    test kit (RequirementReporter, ConcurrentRunner, Perf, MutableClock); don't edit
```

- **Which folder:** the first argument of a command, if it is a folder. Otherwise run
  `./lld.sh list` and use the only one or the newest, and say which in your first line.
- **Everything you write goes in that folder.** Never write into the harness's own
  `src/main/java/com/lld/core`, the project root, or another problem folder.
- **Package:** the one Design.txt names; else the one the files already in `main/` use; else the
  folder name in lower case (`lrucache`, `movieticketbooking`). Code in `main/<pkg path>/`, tests
  in `test/<pkg path>/`.
- No `pom.xml` in the folder yet: run `./lld.sh init <folder>` first. It never touches Design.txt.

## Design.txt: read-only, and the contract

1. **Never create, edit, reformat or rewrite Design.txt**, and never write a restated copy of it
   (no DESIGN.md, no "cleaned-up" version). It can be in any format; read what is there.
2. **Signatures are a contract.** Build the classes, interfaces, enums, records, fields and
   methods exactly as declared: same names, parameter types and order, return types, generics
   and exceptions.
3. **Build only what it names.** No Manager, Orchestrator, Service, Repository, Facade, Factory,
   Demo, or an interface per class unless Design.txt names it. Private helpers are fine. Never
   write a class that only forwards calls to another. If Design.txt names two classes that would
   do the same job, build them as written and flag it in one line.
4. **Gaps:** where Design.txt is silent (a parameter type, a field, an exception), make the plain
   choice and list it under "Filled gaps" in your reply.
5. **Improving it is allowed when an FR or NFR needs it** (thread safety, O(1), a return value an
   FR demands), or when I ask for a pattern with `pattern`. Every change from what Design.txt says
   is a **DRIFT** and is written into the code: as the first line inside every method it affects,
   and above a changed field or an added class:

   ```java
   // DRIFT: <what Design.txt says, or "not in Design.txt"> -> <what the code does>, because <FR-n | NFR-n | pattern Name>: <why>
   // DRIFT: Map<K, V> values (HashMap) -> ConcurrentHashMap + compute(), because NFR-1: check-and-insert must be atomic
   // DRIFT: not in Design.txt -> one ReentrantLock around get/put, because NFR-1: map and recency list change together
   ```
   Counts as drift: a changed or added public method, parameter, return type, field, type, class,
   data structure, or concurrency mechanism (lock, atomic, concurrent collection,
   compute/merge/putIfAbsent). Not drift: private helpers, local variables, getters for declared
   fields, equals/hashCode/toString.
6. **FR vs signature conflict** (the FR says put returns the evicted key, the signature returns
   void): the FR wins. Change the signature, mark it DRIFT citing the FR, and say so at the top
   of your reply.
7. Every reply that changed main code ends with **Drift** (the DRIFT lines you added, or "none")
   and **Filled gaps**. `./lld.sh drift <folder>` lists all DRIFT lines at any time.

## Behaviour comes from the FRs

- Every behaviour statement in an FR is implemented and tested: what is **returned** (including in
  the edge case), what is **thrown**, and what **changes**. "put on a full cache evicts the LRU key
  and returns it" means `put` returns that key, and a test asserts exactly that.
- Exceptions: the ones Design.txt names; otherwise standard Java: `IllegalArgumentException`
  (bad argument), `NullPointerException` via `Objects.requireNonNull` (null not allowed),
  `NoSuchElementException` (unknown id or key), `IllegalStateException` (not allowed in the current
  state). `UnsupportedOperationException("TODO FR-n")` only for bodies not written yet.
- Return `Optional` or `null` exactly as the signature says.

## How to behave

- Do exactly what was asked, in the files asked. No extra features, no "while I'm here" refactors.
- Don't volunteer architecture or the core algorithm. Suggest an approach only on `@hint` /
  `/hint` or a direct question. Implement the approach I describe; if it has a correctness bug
  (not a style preference), flag it in one line.
- Never weaken or delete a test to make it pass. If you think a test contradicts Design.txt, say
  why and wait.
- Code first, prose last.
- It must compile: `./lld.sh compile <folder>` after writing Java, `./lld.sh test <folder> fr` (or
  `nfr`) after changing behaviour. Report failures verbatim.

## Commands

The same instructions exist as `@name` in Kiro CLI (`.kiro/prompts/`) and `/name` in Claude Code
(`.claude/commands/`). Each takes the problem folder as its first argument (optional):
scaffold → fr-tests → (candidate prompts the implementation) → pattern → nfr-tests → audit → hld;
hint only when asked.

## Tests

- In `<folder>/test/<pkg>/`, same package as the code: `<Name>FrTest` and `<Name>NfrTest`, where
  `<Name>` is the main class or entry point from Design.txt (`LRUCacheFrTest`).
- JUnit 5, `@ExtendWith(RequirementReporter.class)`, `@TestMethodOrder(MethodOrderer.MethodName.class)`,
  `@DisplayName("FR-n happy: ...")` in plain words. Import `com.lld.core.RequirementReporter`
  (and `ConcurrentRunner`, `Perf` for NFRs) from the test kit.
- Tests call the public API Design.txt declares. No reflection, no reaching into private fields.
- Deterministic: if Design.txt has time, the code takes a `java.time.Clock`; tests pass
  `Clock.fixed(...)`, or `com.lld.core.MutableClock` from the test kit (`clock.advance(Duration)`)
  when time must move. Never `Thread.sleep` in FR tests.
- Run: `./lld.sh test <folder> fr | nfr | all | Class#method`. A reply that writes or changes
  tests ends with the output of `./lld.sh howto <folder>`: the exact commands to run them.

## Conventions (only where Design.txt doesn't decide)

- Value objects are `record`s validated in the compact constructor; enums carry behaviour; a state
  machine is an enum with its allowed transitions, and illegal moves throw.
- Money is `long`, never `double`. Time comes from an injected `Clock`, never `Instant.now()`.
- No static mutable state, no singletons.

## Concurrency (whenever an NFR mentions threads, concurrency or consistency)

- Shared maps: `ConcurrentHashMap`; uniqueness claims: `putIfAbsent` / `computeIfAbsent`.
- Claiming one resource: CAS (`AtomicReference.compareAndSet`) or a per-key lock.
- Several structures that must change together (a map plus a linked list): one lock around the
  whole operation; say why it is not a bottleneck, or why striping would be the next step.
- Several resources at once: lock in a fixed (sorted) order. Never hold a lock across a slow call.
- Harness building blocks (`KeyedLockManager`, `ExpiringHolds`, `IdempotencyStore`, `SingleFlight`,
  `RetryPolicy`, `EventBus`, `realtime/`) are not in the folder by default. Use one only when an
  NFR needs it: `./lld.sh core <folder>` copies them into `main/com/lld/core`, and the use is a
  DRIFT unless Design.txt names it.

## Review mode (audit)

Punch list, most severe first: `file:line: problem — failure scenario — smallest fix`. Start with
Design.txt conformance (signatures, unmarked drift, classes it doesn't name, forwarding-only
classes, FR behaviour without a test), then races, SOLID, edge cases, `double` / `Instant.now()` /
static state. Don't change code.

## HLD mode (hld)

Follow `hld/TEMPLATE.md`; write `<folder>/HLD.md`. Map each main class or aggregate to a service.
Translate each in-process mechanism to its distributed form: CAS → conditional write / version
column; per-key lock → `SELECT ... FOR UPDATE` or a Redis lock with a fencing token; in-process
events → Kafka topic with a stated partition key; holds with a timeout → Redis `SET NX PX` or a
held_until column; idempotency → idempotency table in the same transaction. Diagrams in Mermaid.
