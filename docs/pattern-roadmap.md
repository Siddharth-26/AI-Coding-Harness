# Pattern roadmap

How to use it: once the classes from `/scaffold <folder>` compile, read your FRs, find the signals
below, and add patterns one at a time with

```
/pattern <folder> <Pattern> — <where it goes> — <intuition: what varies / reacts / validates>
```

(Kiro CLI: `@pattern ...`; the folder can be left out when you have only one.) The agent builds the
pattern inside your problem folder, keeps the signatures your Design.txt declares and the tests
unchanged, marks anything Design.txt doesn't have with a `// DRIFT:` comment, and tells you the
extension point. Every card below ends with a ready-to-edit `/pattern` line (add your folder).

The examples use a repository / manager / facade shape because that is a common way to write a
Design.txt. Nothing generates that shape unless your Design.txt declares those classes; with a single
class (an LRU cache, a rate limiter) the pattern plugs into that class. `create()` in the examples
means wherever your code builds its objects: a constructor, a static factory, or the test.

---

## The roadmap: what to look for, in this order

| Step | Signal in Design.txt | Pattern | Lives in |
|---|---|---|---|
| 0 | Your Design.txt declares a facade, repositories and managers | **Facade + Repository + Manager** (interfaces everywhere, DIP) | the classes you declare |
| 1 | status, lifecycle, "can only X after Y" | **State** | model |
| 2 | "types of", "depends on", "policy", "configurable rule" | **Strategy** (+ **Factory** to pick one) | `<concept>/`, used by a manager |
| 3 | add-ons that stack: taxes, surcharges, retries, logging | **Decorator** | `<concept>/` |
| 4 | "notify", "when X happens", "subscribe", "alert" | **Observer** | manager publishes, listeners in `<concept>/` |
| 5 | "validate", "eligible", "rules", "approval levels" | **Chain of Responsibility** or **Specification** | `<concept>/` (e.g. `validation/`) |
| 6 | "undo", "history", "replay", "queue requests" | **Command** (+ **Memento**) | manager / `<concept>/` |
| 7 | third-party provider, gateway, "SMS/email/payment provider" | **Adapter** (+ **Null Object**, **Proxy**) | `<concept>/` |
| 8 | "many optional fields", "configure an X" | **Builder** | model |
| 9 | tree, folder, nested, "category of categories" | **Composite** | model |
| 10 | participants coordinating through one place (room, auction, control tower) | **Mediator** | manager |
| 11 | "same steps, some differ per type" | **Template Method** | `<concept>/` |

Rule of thumb: patterns 0-5 cover almost every machine-coding round. Add a pattern only when you can
name what becomes extensible, and say that sentence out loud.

---

## 0. Facade + Repository + Manager (only when your Design.txt declares them)

Nothing generates this shape: `/scaffold` builds only the classes your Design.txt declares. When you
do declare these layers, this is what each one owns, and each must own something: a facade that only
forwards to a single manager is a duplicate layer, so drop one of them. (`Repository`,
`InMemoryRepository`, `EventBus` and `IdGenerator` are harness building blocks: `./lld.sh core
<folder>` copies them into your folder.)

```java
// repository/  storage only
public interface LoanRepository extends Repository<Loan> {
    List<Loan> findActiveByMember(String memberId);
}

public class InMemoryLoanRepository extends InMemoryRepository<Loan> implements LoanRepository {
    public InMemoryLoanRepository() { super("Loan"); }

    @Override
    public List<Loan> findActiveByMember(String memberId) {
        return findWhere(l -> l.getMemberId().equals(memberId) && l.isActive());
    }
}

// manager/  business rules; depends only on interfaces, all injected
public interface LoanManager {
    Loan borrow(String memberId, String bookId);
    Loan returnBook(String loanId);
}

public class DefaultLoanManager implements LoanManager {
    private final LoanRepository loans;
    private final MemberRepository members;
    private final EventBus events;
    private final Clock clock;

    public DefaultLoanManager(LoanRepository loans, MemberRepository members, EventBus events, Clock clock) {
        this.loans = loans; this.members = members; this.events = events; this.clock = clock;
    }

    @Override
    public Loan borrow(String memberId, String bookId) {
        members.getOrThrow(memberId);
        if (loans.findActiveByMember(memberId).size() >= 3) {
            throw new ConflictException("member " + memberId + " already has 3 active loans");
        }
        Loan loan = loans.save(new Loan(IdGenerator.next("LN"), memberId, bookId, clock.instant()));
        events.publish(new BookBorrowed(loan.getId(), memberId, bookId));
        return loan;
    }

    @Override
    public Loan returnBook(String loanId) {
        Loan loan = loans.getOrThrow(loanId);
        loan.markReturned(clock.instant());
        return loans.save(loan);
    }
}

// the facade: single entry point + composition root
public class LibraryOrchestrator {
    private final LoanManager loans;

    public LibraryOrchestrator(LoanManager loans) { this.loans = loans; }

    public static LibraryOrchestrator create(Clock clock, EventBus events) {
        MemberRepository members = new InMemoryMemberRepository();
        return new LibraryOrchestrator(new DefaultLoanManager(new InMemoryLoanRepository(), members, events, clock));
    }

    public Loan borrow(String memberId, String bookId) { return loans.borrow(memberId, bookId); }
    public Loan returnBook(String loanId) { return loans.returnBook(loanId); }
}
```
**Principles:** SRP (one reason to change per layer), DIP (managers see interfaces), and a facade
that hides the wiring from callers.
**Say:** "Tests and clients only know the orchestrator; I can swap storage or rules without touching them."

---

## 1. State

**Signal:** an entity has a status, and what's allowed depends on it.
**Lives in:** model. **Principle:** OCP; no status `if`-chains in managers.

Default: an enum with a transition table. Use it when states only differ in which moves are legal.
```java
public enum LoanStatus {
    ACTIVE, OVERDUE, RETURNED, LOST;

    private static final Map<LoanStatus, Set<LoanStatus>> ALLOWED = Map.of(
            ACTIVE, EnumSet.of(OVERDUE, RETURNED, LOST),
            OVERDUE, EnumSet.of(RETURNED, LOST),
            RETURNED, EnumSet.noneOf(LoanStatus.class),
            LOST, EnumSet.noneOf(LoanStatus.class));

    public boolean canTransitionTo(LoanStatus next) { return ALLOWED.get(this).contains(next); }
    public boolean isTerminal() { return ALLOWED.get(this).isEmpty(); }
}
// in the entity:
private void transitionTo(LoanStatus next) {
    if (!status.canTransitionTo(next)) throw new InvalidStateTransitionException("Loan " + id, status, next);
    status = next;
}
```
Class-per-state (GoF): use it when **the same action behaves differently** per state (vending
machine, elevator, ATM). The context delegates, so there's no `if (state == ...)`.
```java
public interface MachineState {
    default MachineState insertCoin(VendingMachine m, long amount) { throw new ConflictException("can't insert coin now"); }
    default MachineState select(VendingMachine m, String code)    { throw new ConflictException("can't select now"); }
}
public class Idle implements MachineState {
    public MachineState insertCoin(VendingMachine m, long amount) { m.addBalance(amount); return new HasMoney(); }
}
public class HasMoney implements MachineState {
    public MachineState insertCoin(VendingMachine m, long amount) { m.addBalance(amount); return this; }
    public MachineState select(VendingMachine m, String code) { m.dispense(code); return new Idle(); }
}
// VendingMachine: state = state.insertCoin(this, amount);
```
**Extend:** a new state is one enum constant plus one map entry, or one new class.
**Prompt:** `/pattern State — Loan lifecycle in model — returns and fines only allowed for ACTIVE/OVERDUE loans`
**Skip when:** there are only two states and one transition; a boolean is honest.

---

## 2. Strategy (+ Factory to choose)

**Signal:** one decision has several interchangeable rules, chosen by type, config, or time
("fine depends on membership", "allocation policy", "split EQUAL/EXACT/PERCENT").
**Lives in:** `<concept>/` (e.g. `pricing/`); the manager holds the interface. **Principle:** OCP, DIP.
```java
public interface FinePolicy {
    long fineFor(Loan loan, Instant returnedAt);
}

public class PerDayFinePolicy implements FinePolicy {
    private final long perDay;
    private final Duration freePeriod;
    public PerDayFinePolicy(long perDay, Duration freePeriod) { this.perDay = perDay; this.freePeriod = freePeriod; }

    @Override
    public long fineFor(Loan loan, Instant returnedAt) {
        long lateDays = Duration.between(loan.getBorrowedAt().plus(freePeriod), returnedAt).toDays();
        return Math.max(0, lateDays) * perDay;
    }
}

public class NoFinePolicy implements FinePolicy {          // premium members
    @Override public long fineFor(Loan loan, Instant returnedAt) { return 0; }
}

// choosing one: a registry keyed by type instead of a switch in the manager
Map<MembershipType, FinePolicy> policies = new EnumMap<>(Map.of(
        MembershipType.REGULAR, new PerDayFinePolicy(10, Duration.ofDays(14)),
        MembershipType.PREMIUM, new NoFinePolicy()));
```
**Extend:** a new rule is a new class plus one registry entry in `create()`.
**Prompt:** `/pattern Strategy — fine calculation in LoanManager — fine rules differ by membership type`
**Skip when:** only one rule will ever exist.

**Factory** is the creation side of the same idea. Use it when the caller knows *which kind* but shouldn't know *which class*:
```java
public class NotifierFactory {
    private final Map<Channel, Supplier<Notifier>> creators = new EnumMap<>(Channel.class);
    public NotifierFactory register(Channel channel, Supplier<Notifier> creator) { creators.put(channel, creator); return this; }
    public Notifier create(Channel channel) {
        Supplier<Notifier> creator = creators.get(channel);
        if (creator == null) throw new ValidationException("no notifier for " + channel);
        return creator.get();
    }
}
```
**Prompt:** `/pattern Factory — Notifier creation in NotificationManager — channel decided at runtime by user preference`
A closed set that never grows (chess pieces) can use a `switch` expression instead.

---

## 3. Decorator

**Signal:** optional behaviours that **stack** around a core one: taxes, surcharges, discounts on a
price; retry, logging or caching around a call.
**Lives in:** `<concept>/`. **Principle:** OCP; composition over inheritance.
```java
public interface PriceCalculator { long price(Order order); }

public class BasePrice implements PriceCalculator {
    public long price(Order order) { return order.itemsTotal(); }
}
public class PackagingFee implements PriceCalculator {
    private final PriceCalculator inner; private final long fee;
    public PackagingFee(PriceCalculator inner, long fee) { this.inner = inner; this.fee = fee; }
    public long price(Order order) { return inner.price(order) + fee; }
}
public class Gst implements PriceCalculator {
    private final PriceCalculator inner; private final int percent;
    public Gst(PriceCalculator inner, int percent) { this.inner = inner; this.percent = percent; }
    public long price(Order order) { long p = inner.price(order); return p + p * percent / 100; }
}
// create(): new Gst(new PackagingFee(new BasePrice(), 20), 5)
```
**Extend:** a new add-on is a new wrapper class; the order of wrapping is visible in `create()`.
**Prompt:** `/pattern Decorator — price calculation in OrderManager — packaging fee, GST and discounts stack on the base price`
**Skip when:** the add-ons never combine. Then it's just a Strategy.

---

## 4. Observer

**Signal:** "when X happens, also do A, B, C": notifications, audit, analytics, triggering the
next step.
**Lives in:** the manager publishes; listeners live in `<concept>/` (e.g. `notification/`).
**Principle:** SRP; the manager doesn't know who reacts.
```java
public record BookReturned(String loanId, String memberId, long fine) {}

// manager, after the state change succeeds:
events.publish(new BookReturned(loan.getId(), loan.getMemberId(), fine));

// listeners, registered in create():
events.subscribe(BookReturned.class, e -> notifier.send(e.memberId(), "Returned. Fine: Rs " + e.fine()));
events.subscribe(BookReturned.class, e -> waitlist.offerNext(e.loanId()));
```
`com.lld.core.EventBus` isolates a failing listener, so one broken SMS doesn't fail the return.
**Extend:** a new reaction is a new `subscribe` line; the manager is untouched.
**Prompt:** `/pattern Observer — loan events from LoanManager — notify member and waitlist on return, without LoanManager knowing them`
**Say:** "In the HLD this becomes a Kafka topic partitioned by memberId."

---

## 5a. Chain of Responsibility

**Signal:** a request passes a series of checks or handlers that change over time (eligibility,
approval levels, validation pipeline).
**Lives in:** `<concept>/` (e.g. `validation/`). **Principle:** OCP, SRP (one rule per class).

All-must-pass variant (the common one):
```java
public interface BorrowRule { void check(Member member, Book book); }   // throws ValidationException / ConflictException

public class BorrowValidator {
    private final List<BorrowRule> rules;
    public BorrowValidator(List<BorrowRule> rules) { this.rules = List.copyOf(rules); }
    public void validate(Member member, Book book) { rules.forEach(r -> r.check(member, book)); }
}
// create(): new BorrowValidator(List.of(new MembershipActive(), new NoUnpaidFines(), new BookAvailable()))
```
First-capable-handler-wins variant (escalation, approval limits):
```java
public abstract class Approver {
    private Approver next;
    public Approver then(Approver next) { this.next = next; return next; }
    public void approve(Refund r) {
        if (canApprove(r)) doApprove(r);
        else if (next != null) next.approve(r);
        else throw new ConflictException("nobody can approve " + r.amount());
    }
    protected abstract boolean canApprove(Refund r);
    protected abstract void doApprove(Refund r);
}
```
**Extend:** a new rule is a new class added to the list; order is explicit.
**Prompt:** `/pattern Chain of Responsibility — borrow eligibility in LoanManager — checks will keep growing (fines, membership, book availability)`

## 5b. Specification

**Signal:** search or filter by criteria that users combine ("available AND fiction AND NOT reserved").
**Lives in:** `<concept>/` (e.g. `search/`). **Principle:** OCP; criteria compose instead of multiplying finder methods.
```java
@FunctionalInterface
public interface Spec<T> {
    boolean isSatisfiedBy(T t);
    default Spec<T> and(Spec<T> other) { return t -> isSatisfiedBy(t) && other.isSatisfiedBy(t); }
    default Spec<T> or(Spec<T> other)  { return t -> isSatisfiedBy(t) || other.isSatisfiedBy(t); }
    default Spec<T> not()              { return t -> !isSatisfiedBy(t); }
}
// Spec<Book> query = BookSpecs.genre("fiction").and(BookSpecs.available()).and(BookSpecs.reserved().not());
// repository: findWhere(query::isSatisfiedBy)
```
**Prompt:** `/pattern Specification — book search in CatalogManager — users combine filters freely`

---

## 6. Command (+ Memento)

**Signal:** undo/redo, history, replay, or requests that are queued and executed later (elevator
requests, scheduled jobs).
**Lives in:** manager or `<concept>/`. **Principle:** SRP; the request becomes an object.
```java
public interface Command {
    void execute();
    void undo();
}

public class CommandHistory {
    private final Deque<Command> done = new ArrayDeque<>();
    private final Deque<Command> undone = new ArrayDeque<>();

    public void run(Command c) { c.execute(); done.push(c); undone.clear(); }
    public void undo() { if (!done.isEmpty()) { Command c = done.pop(); c.undo(); undone.push(c); } }
    public void redo() { if (!undone.isEmpty()) { Command c = undone.pop(); c.execute(); done.push(c); } }
}
```
**Memento**, when undoing is easier by snapshot than by inverse operation:
```java
public record BoardSnapshot(String[][] cells, String nextPlayer) {}   // immutable copy of state
// originator: BoardSnapshot save(); void restore(BoardSnapshot s); caretaker: Deque<BoardSnapshot>
```
**Prompt:** `/pattern Command — edits in EditorManager — every edit must be undoable and redoable`

---

## 7. Adapter (+ Null Object, Proxy)

**Adapter.** **Signal:** a third-party API whose shape doesn't match your interface (SMS vendor,
payment gateway, maps API).
**Lives in:** `<concept>/` (e.g. `notification/`). **Principle:** DIP; your code depends on your interface.
```java
public class TwilioLikeSmsAdapter implements Notifier {           // our interface
    private final VendorSmsClient client;                           // their class, their method names
    public TwilioLikeSmsAdapter(VendorSmsClient client) { this.client = client; }
    @Override public void send(String userId, String message) {
        int status = client.sendText(lookupPhone(userId), message, "LIBRARY");
        if (status >= 500) throw new TransientException("sms vendor returned " + status);
    }
    private String lookupPhone(String userId) { return "+91-0000000000"; }  // stub: comes from a UserRepository
}
```
**Prompt:** `/pattern Adapter — SMS sending in notification — vendor client has a different API and error codes`

**Null Object.** **Signal:** "optional" collaborators that lead to `if (x != null)` checks.
```java
public class NoOpNotifier implements Notifier { public void send(String userId, String message) { } }
```

**Proxy.** **Signal:** the same interface, with caching, lazy loading or access control in front.
```java
public class CachingBookRepository implements BookRepository {    // same interface as the real one
    private final BookRepository real;
    private final Map<String, Book> cache = new ConcurrentHashMap<>();
    public CachingBookRepository(BookRepository real) { this.real = real; }
    @Override public Optional<Book> findById(String id) {
        return Optional.ofNullable(cache.computeIfAbsent(id, k -> real.findById(k).orElse(null)));
    }
    // other methods delegate to real; save() also updates or evicts the cache
}
```

---

## 8. Builder

**Signal:** an object with many optional fields or cross-field validation (a coupon, a search
query, a notification request). Use a constructor or a record for 3 fields or fewer.
**Lives in:** model.
```java
public final class Coupon {
    private final String code; private final long flatOff; private final int percentOff; private final Instant expiresAt;
    private Coupon(Builder b) { code = b.code; flatOff = b.flatOff; percentOff = b.percentOff; expiresAt = b.expiresAt; }
    public static Builder builder(String code) { return new Builder(code); }

    public static final class Builder {
        private final String code; private long flatOff; private int percentOff; private Instant expiresAt;
        private Builder(String code) { this.code = code; }
        public Builder flatOff(long v)      { flatOff = v; return this; }
        public Builder percentOff(int v)    { percentOff = v; return this; }
        public Builder expiresAt(Instant t) { expiresAt = t; return this; }
        public Coupon build() {
            if ((flatOff > 0) == (percentOff > 0)) throw new ValidationException("exactly one of flatOff / percentOff");
            if (expiresAt == null) throw new ValidationException("expiresAt is required");
            return new Coupon(this);
        }
    }
}
```
**Prompt:** `/pattern Builder — Coupon in model — many optional fields, flat and percent are mutually exclusive`

---

## 9. Composite

**Signal:** a tree whose leaves and branches are treated the same (files and folders, menu
categories, org chart, bundles of products).
**Lives in:** model. **Principle:** LSP; clients don't care whether a node is a leaf.
```java
public interface Node { String name(); long size(); }

public record FileNode(String name, long size) implements Node {}

public class DirectoryNode implements Node {
    private final String name;
    private final Map<String, Node> children = new TreeMap<>();     // sorted listing for `ls`
    public DirectoryNode(String name) { this.name = name; }
    public void add(Node child) {
        if (children.putIfAbsent(child.name(), child) != null) throw new ConflictException(child.name() + " already exists");
    }
    public String name() { return name; }
    public long size() { return children.values().stream().mapToLong(Node::size).sum(); }
}
```
**Prompt:** `/pattern Composite — folders and files in model — size and listing must work the same at any depth`

---

## 10. Mediator

**Signal:** many participants would otherwise reference each other (chat room members, auction
bidders, runway requests to a control tower).
**Lives in:** manager. **Principle:** fewer couplings, one place for the interaction rules.
```java
public class AuctionRoom {                                   // the mediator
    private final Map<String, Consumer<String>> bidders = new ConcurrentHashMap<>();
    private long highest;
    private String leader;

    public void join(String bidderId, Consumer<String> inbox) { bidders.put(bidderId, inbox); }

    public synchronized void bid(String bidderId, long amount) {
        if (amount <= highest) throw new ConflictException("bid must beat " + highest);
        highest = amount; leader = bidderId;
        bidders.forEach((id, inbox) -> { if (!id.equals(bidderId)) inbox.accept(bidderId + " bid " + amount); });
    }
}
```
**Prompt:** `/pattern Mediator — bidding in AuctionManager — bidders must not know each other; rules live in one place`

---

## 11. Template Method

**Signal:** several types share the same steps in the same order, but one or two steps differ
(payment methods: validate → charge → receipt; report exports).
**Lives in:** `<concept>/`. **Principle:** DRY for the skeleton; the variation stays explicit.
```java
public abstract class PaymentFlow {
    public final Receipt pay(Order order) {                  // the fixed skeleton
        validate(order);
        String txnId = charge(order.total());
        return new Receipt(order.getId(), txnId, order.total());
    }
    protected void validate(Order order) {
        if (order.total() <= 0) throw new ValidationException("nothing to pay");
    }
    protected abstract String charge(long amount);           // the step that varies
}
public class UpiPayment extends PaymentFlow {
    protected String charge(long amount) { return "UPI-" + amount; }
}
```
**Prompt:** `/pattern Template Method — payment in PaymentManager — UPI/card/wallet share steps, only charging differs`
**Prefer Strategy** when the varying step can be swapped at runtime or combined with others.

---

## Singleton: know it, avoid it

Global state hides dependencies and breaks tests. In this harness each shared object (repositories,
EventBus, lock manager) is created **once in `create()`** and injected. If asked to write one:
`public enum Registry { INSTANCE; ... }` (thread-safe, serialization-safe), or the holder idiom.
Say: "One instance, yes. Global access, no: I inject it."

## Rarely needed (name them, don't reach for them)

Abstract Factory (families of related objects: UI themes), Prototype (cloning configured objects),
Flyweight (sharing immutable intrinsic state: thousands of identical seat types or chess piece
glyphs), Iterator (Java already has it), Visitor (new operations over a fixed type hierarchy, e.g.
tax and export over a Composite), Interpreter (tiny rule languages).

---

## SOLID: the smell and the fix

- **S**: a manager computes prices inline → extract a Strategy. An entity sends SMS → publish an event.
- **O**: the same `switch (type)` in several places → enum behaviour, Strategy or Factory registry.
- **L**: `ElectricCar extends Car` throws on `refuel()` → wrong hierarchy; model capabilities as interfaces.
- **I**: a fat `Vehicle` interface with `fly()` → split into role interfaces.
- **D**: `new InMemoryXRepository()` inside a manager → inject the interface; `Instant.now()` → inject `Clock`.

## Concurrency idioms (Tekion asks "is this thread-safe?")

| Situation | Idiom | Distributed equivalent (HLD) |
|---|---|---|
| Uniqueness (one active X per user, idempotency key) | `map.putIfAbsent(k, v) != null → reject` | unique constraint / idempotency table |
| Claim one slot (seat, rider, spot) | `AtomicReference.compareAndSet(null, me)` | `UPDATE ... WHERE holder IS NULL` |
| Decrement stock without going negative | `getAndUpdate(s -> s > 0 ? s - 1 : s)`; before == 0 → sold out | `UPDATE ... SET qty = qty - 1 WHERE qty > 0` |
| Multi-step change on one entity | `KeyedLockManager.withLock(id, ...)` | `SELECT ... FOR UPDATE` / version column |
| Several resources at once | `KeyedLockManager.withLocks(ids, ...)` (sorted, no deadlock) | lock rows in id order in one transaction |
| Expiring holds | `ExpiringHolds` | Redis `SET key owner NX PX ttl` |
| Retries must not repeat side effects | `IdempotencyStore` | idempotency table in the same transaction |
| Hot key, many identical loads | `SingleFlight` | single-flight per node + stale-while-revalidate |

Optimistic (CAS / version) when conflicts are rare; pessimistic (locks) when contention is high
or retries are expensive.
