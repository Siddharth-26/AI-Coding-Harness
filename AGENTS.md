# AGENTS.md: machine-coding harness (core Java 17)

Read by Claude Code (via CLAUDE.md), Codex, Cursor, Kiro and most other agents.

## Situation

A live, timed LLD machine-coding round on a problem nobody has seen before. The candidate owns
every design decision and must be able to explain every line. `Design.txt` in the project root is
the source of truth: problem, package, FRs, NFRs, entities, repositories, managers, the
orchestrator, and the patterns to use. You turn it into clean, compiling, tested Java, one step at
a time and only when asked.

## How to behave

- Generate exactly what was asked, in the files asked. No extra features or "while I'm here"
  refactors. If something else is needed to compile, say so in one line.
- Follow `Design.txt`. If it is ambiguous in a way that changes the design, ask one question
  instead of guessing. Never edit `Design.txt` yourself.
- Don't volunteer architecture or the core algorithm. Suggest an approach only when I run `/hint`
  or ask directly. Implement the approach I describe; if it has a correctness bug (not a style
  preference), flag it in one line.
- Code first, prose last: at most 3 bullets of assumptions afterwards.
- It must compile: run `mvn -q compile` after writing Java, and the relevant test script after
  changing behaviour. Report failures verbatim.

## Commands

The same instructions exist for both tools: `@name` in Kiro CLI (`.kiro/prompts/`), `/name` in
Claude Code (`.claude/commands/`). scaffold → fr-tests → (candidate implements) → pattern → nfr-tests →
audit → hld; hint only when asked.

## Layers (always this shape)

```
com.lld.<package>/
  model/        entities, value objects, enums (+ their transition tables)
  repository/   <Entity>Repository (interface) + InMemory<Entity>Repository
  manager/      <Area>Manager (interface) + Default<Area>Manager
  <concept>/    pattern classes, named after what varies: pricing/, assignment/, notification/, validation/
  <Problem>Orchestrator.java   the facade: the only entry point for tests and Demo
  Demo.java     scripted walkthrough using the orchestrator
```

| Layer | Owns | Must not |
|---|---|---|
| **Model** | Data, invariants and state transitions of ONE entity, through intention-revealing methods (`order.cancel()`, not `setStatus`) | Touch repositories, managers, I/O or other aggregates |
| **Repository** | Storage only. The interface extends `com.lld.core.Repository<T>` and adds the finders listed in Design.txt; `InMemory<Entity>Repository extends InMemoryRepository<T> implements <Entity>Repository`, with finders built on `findWhere` | Contain business rules |
| **Manager** | Business rules for one entity or area. Depends on repository **interfaces**, strategies, `EventBus`, `Clock`, all constructor-injected. Writes only its own entity's repository; may read others | Call another manager, build its own collaborators (`new`), read the clock directly |
| **Orchestrator (Facade)** | The public API from Design.txt. Holds the managers, delegates, and coordinates flows that span managers (place order → assign rider). `static create(Clock clock, EventBus events)` is the composition root: it wires in-memory repositories, default strategies and managers. A second constructor takes the managers, for tests that need fakes | Contain business rules or touch repositories |

Naming: interfaces carry the role name (`OrderManager`, `OrderRepository`); implementations are
`Default<Area>Manager` and `InMemory<Entity>Repository`.

## Patterns

Patterns are applied one at a time with `/pattern <Pattern> — <where> — <intuition>`, following
`docs/pattern-roadmap.md` for which layer the pattern lives in and what it looks like. Rules:
- The facade's public API and the tests don't change when a pattern is introduced; tests must stay
  as green as they were.
- Each pattern gets a domain-named interface (`DeliveryFeeStrategy`, not `Strategy<I, O>`), is
  wired through constructors in `create(...)`, and is explained by its extension point: "to add X,
  write a class implementing Y and register it in Z; nothing else changes".

## Conventions

- Entities implement `com.lld.core.Identifiable`; ids come from `IdGenerator.next("PREFIX")`;
  identity fields are `final`.
- Value objects are `record`s, validated in the compact constructor with `ValidationException`.
- Enums carry behaviour (e.g. a price multiplier) instead of switches spread across managers.
- State machines are an enum with a static `ALLOWED` `Map<State, Set<State>>` built with `EnumSet`,
  plus `canTransitionTo` and `isTerminal`; the entity's private `transitionTo` throws
  `InvalidStateTransitionException`. Use class-per-state (GoF State) only when the same action
  behaves differently per state.
- Money is `long` (paise or whole rupees), never `double`. Time comes from the injected `Clock`,
  never `Instant.now()`.
- Exceptions from `com.lld.core.exception`: `ValidationException` (bad input), `NotFoundException`,
  `ConflictException` (the current state rejects it), `InvalidStateTransitionException`,
  `TransientException` (retryable infrastructure failure).
- No static mutable state and no singletons except `IdGenerator`.

## Concurrency (always considered, never hand-waved)

- Shared maps: `ConcurrentHashMap`. Uniqueness claims: `putIfAbsent` / `computeIfAbsent`.
- Claiming one resource: CAS (`AtomicReference.compareAndSet`) or `KeyedLockManager.withLock(id, ...)`.
- Claiming several resources: `KeyedLockManager.withLocks(ids, ...)`, which locks in sorted order
  and so cannot deadlock.
- No `synchronized` on whole manager methods, and no global locks. Candidate lists are snapshots:
  re-check under the claim.
- Never hold a lock across a slow external call. Call outside, commit under the lock, and
  compensate if the commit fails.

## Building blocks in `com.lld.core`: reuse, don't re-implement

| Need | Use |
|---|---|
| Storage | `Repository<T>` + `InMemoryRepository<T>` |
| Domain events / notifications | `EventBus` (events are records) |
| Per-entity critical section, multi-resource claim | `KeyedLockManager` |
| Temporary reservation with timeout (seat, room, stock) | `ExpiringHolds` |
| Retries / double submits must not repeat a side effect | `IdempotencyStore` |
| Many concurrent identical loads of a hot key | `SingleFlight` |
| Flaky dependency | `RetryPolicy` (retries `TransientException` only) |
| Real-time push, presence, multiple devices | `PushGateway` / `ConnectionRegistry` |
| Time | inject `Clock`; `MutableClock` in Demo and tests |

## Tests

- Tests call **only the orchestrator** (black box), wired with `create(clock, events)` and a
  `MutableClock`.
- `<Problem>FrTest` proves the FRs (a happy test and a negative test per FR). `<Problem>NfrTest`
  checks the NFRs (races via `com.lld.core.ConcurrentRunner`, timing via `com.lld.core.Perf` with
  generous budgets). Both use `@ExtendWith(RequirementReporter.class)`,
  `@TestMethodOrder(MethodOrderer.MethodName.class)` and `@DisplayName("FR-n ...")`.
- Run with `./test-fr.sh` and `./test-nfr.sh` (the package comes from Design.txt). Never
  `Thread.sleep`; move time with `MutableClock.advance`.
- The harness's own tests are named `*SelfCheck` and are not part of a normal run.

## Review mode (/audit)

Punch list, most severe first: `file:line: problem — failure scenario — smallest fix`. Check layer
violations first (rules in the orchestrator, managers calling managers, repositories with logic),
then races, SOLID, edge cases, and `double`/`Instant.now()`/static state. Don't rewrite code.

## HLD mode (/hld)

Follow `hld/TEMPLATE.md`. Each manager/aggregate maps to a service. Translate each in-process
mechanism to its distributed form: CAS → conditional write / version column; `KeyedLockManager` →
`SELECT ... FOR UPDATE` or a Redis lock with fencing token; `EventBus` → Kafka topic with a stated
partition key; `ExpiringHolds` → Redis `SET NX PX` or a held_until column; `IdempotencyStore` →
idempotency table in the same transaction. Diagrams in Mermaid.
