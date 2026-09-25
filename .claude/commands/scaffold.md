---
description: Turn Design.txt into compiling layers (model, repository, manager, Orchestrator facade, Demo) with TODO bodies. No business logic.
argument-hint: [optional: only a part, e.g. "model only" or "add PaymentManager"]
---
Scaffold from `Design.txt`. Scope (optional): $ARGUMENTS

Read `Design.txt` (ignore `#` lines) and follow the layer rules in AGENTS.md exactly. Package:
`com.lld.<PACKAGE>`. If a section is still a `<placeholder>`, stop and tell me which one.

Create:
1. `model/`: every entity (`implements Identifiable`, final id + identity fields, constructor, getters,
   no public setters), value objects as `record`s with compact-constructor validation, and enums.
   An enum with `A -> B, C` transitions gets the `ALLOWED` table, `canTransitionTo` and
   `isTerminal`, and its entity gets a private `transitionTo`, plus one public intention-revealing
   method per transition (`cancel()`, `markDelivered()`).
2. `repository/`: for each line in REPOSITORIES, an `<Entity>Repository extends Repository<Entity>` with
   the listed finders, and `InMemory<Entity>Repository extends InMemoryRepository<Entity>` implementing
   them with `findWhere`. Finders are real code, not TODOs.
3. `manager/`: for each line in MANAGERS, an interface with exactly those methods, and
   `Default<Area>Manager` with constructor-injected repositories, `EventBus` and `Clock`.
   - Setup methods that only construct and save (register, add, create, schedule) get real bodies:
     `return repo.save(new X(IdGenerator.next("PFX"), ...));`.
   - Every method that enforces an FR rule gets
     `throw new UnsupportedOperationException("TODO FR-n: <that FR's text>");`, naming the FR(s) it serves.
4. `<Problem>Orchestrator`: the methods in ORCHESTRATOR, each delegating to one manager (real
   delegation code), a constructor taking the managers, and
   `public static <Problem>Orchestrator create(Clock clock, EventBus events)` wiring the in-memory
   repositories and default managers.
5. `Demo.java`: `main` that builds `create(new MutableClock(...), new EventBus())` and prints
   "wiring OK", with a `// TODO walkthrough` comment listing the FRs in order.

Do NOT implement business rules and do NOT apply any pattern from the PATTERNS section (that's
/pattern). Run `mvn -q compile` until it is clean.

Finish with: the file tree, a table "facade method → manager method → FR", and any Design.txt gap
you had to assume (at most 3 bullets). Next step for me: `/fr-tests`.
