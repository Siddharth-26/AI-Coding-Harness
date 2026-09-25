# Hint catalogue: problem shape → core approach

Used by `/hint` and good for revision. Each row: what the problem looks like, the algorithm or
structure that fits, the property that points to it, and what people get wrong. The complexity is
for the key operation.

## Scheduling, allocation, matching

| Problem shape | Core approach | Why it fits | Watch out for |
|---|---|---|---|
| Elevator: serve floor requests | **SCAN / LOOK** per elevator: two `TreeSet<Integer>` (stops above, stops below); take `ceiling(current)` going up and `floor(current)` going down, reverse when the set in the current direction is empty. Dispatcher scores elevators (same direction and approaching < idle nearest < others). O(log n) | Requests are points on a line; sweeping in one direction bounds wait time | FCFS zig-zag and starvation; requests behind the current direction; thread-safe stop sets when buttons are pressed during movement |
| Parking: pick a spot | Per (size) **ordered free-spot set** (`TreeSet`/`PriorityQueue` by floor, id); best fit = try required size, then next bigger; claim atomically (`poll` under a per-size lock, or CAS on the spot). O(log n) | "Smallest that fits, nearest first" is an ordered lookup, not a scan | O(n) scan of all spots per entry; giving a LARGE spot to a bike while SMALL is free; releasing a spot twice |
| Meeting rooms / calendar conflicts | Per room `TreeMap<start, Meeting>`: conflict if `floorEntry(start).end > start` or `ceilingEntry(start).start < end`. Minimum rooms: sort by start + **min-heap of end times**. O(log n) | Overlap only needs the neighbours before and after | Use half-open `[start, end)` so back-to-back meetings don't clash; time zones; recurring meetings |
| Hotel / rental: availability for a date range | Per room `TreeMap<checkIn, Booking>` with the same overlap check; or per room type a **per-night inventory counter**, decremented for every night under locks taken in date order | A range booking is interval overlap; type-level search is a count per night | Checkout day is free (`[in, out)`); atomic across all nights (all or none); lock nights in sorted order |
| Movie / flight seats | Per-seat state + **expiring hold**; multi-seat hold is all-or-nothing under **sorted per-seat locks** (`ExpiringHolds`); adjacent seats = sliding window over a row | Contention is per seat; holds must expire when payment is abandoned | Hold expiring during payment → confirm must re-check under the lock; deadlock without sorted locking |
| Food delivery / cab: assign nearest rider | **Grid / geohash buckets** of available riders → candidates from the nearby cells sorted by distance/ETA (haversine) → claim with **CAS AVAILABLE→ASSIGNED**, fall back to the next candidate; timeout → reassign | Nearest-neighbour search must not scan every rider; many orders race for the same rider | Two orders taking the same rider; stale locations; empty neighbour cells (expand the ring) |
| Round-robin / load-balanced assignment (support agents, delivery zones) | `AtomicInteger` index mod n, or a **min-heap by current load** | Fairness is either rotation or least-loaded | Removing agents mid-rotation; ties |
| Task queue with priorities / SLAs | `PriorityBlockingQueue` ordered by (priority, createdAt) + worker threads | Always serve the most urgent first, FIFO within a priority | Starvation of low priority (ageing); comparator must be consistent with equals |

## Time-based behaviour

| Problem shape | Core approach | Why it fits | Watch out for |
|---|---|---|---|
| "Notify after 1 hour", reminders, delayed jobs | `DelayQueue` / `PriorityQueue` by fire time + a worker; in tests, an injected `Clock` and a `tick()` that fires everything due | Next job to run is always the earliest | `Thread.sleep` in tests; cancellation (remove by id or a tombstone) |
| Rate limiter | **Token bucket** (refill = elapsed × rate, capped; refill lazily on each request) or **sliding window log** (deque of timestamps) or **sliding window counter** (weighted previous window); per-key state in `ConcurrentHashMap` updated atomically | Token bucket allows bursts up to the capacity with a steady average rate | Fixed-window burst at the boundary; not injecting the clock; non-atomic check-then-decrement |
| TTL cache / session expiry | Store `expiresAt`, treat expired entries as absent on read (lazy) + periodic sweep | No timer per entry | Clock injection; sweeping while readers are active |
| Retries for flaky calls | Exponential backoff + full jitter, retry only transient errors, max attempts, then a dead-letter list (`RetryPolicy`) | Spreads retries so failed clients don't retry together | Retrying non-idempotent calls; retrying business errors |

## Caches and lookups

| Problem shape | Core approach | Why it fits | Watch out for |
|---|---|---|---|
| LRU cache | `HashMap<K, Node>` + **doubly linked list** (or `LinkedHashMap(accessOrder=true)` + `removeEldestEntry`). O(1) | Recency = position in a list you can splice in O(1) | `get` must move the entry to the front; capacity 0; thread safety (one lock, or segment it) |
| LFU cache | `HashMap<K, Node(value, freq)>` + `HashMap<freq, LinkedHashSet<K>>` + `minFreq`. On access move to `freq+1` and bump `minFreq` if its bucket empties; on insert `minFreq = 1`. O(1) | Buckets by frequency with LRU order inside each bucket | Resetting `minFreq` on insert; tie-break by recency |
| Autocomplete / prefix search | **Trie**; each node keeps its top-k completions (updated on insert), or DFS + min-heap of size k at query time | Every query is a prefix walk | Updating top-k when frequencies change; memory (compress single-child chains) |
| Leaderboard / top-K | `TreeMap<score, Set<player>>` + `Map<player, score>`; a streaming top-k is a **min-heap of size k**. O(log n) | Ordered by score with fast rank changes | Equal scores; updating a player means remove then re-insert |
| URL shortener | **Base62** of a unique id (counter / Snowflake); custom alias via `putIfAbsent`. 7 chars ≈ 3.5 trillion codes | Unique ids make collisions impossible | Predictable sequential codes (shuffle or salt); alias races |
| Consistent hashing | `TreeMap<hash, node>` ring with virtual nodes; `ceilingEntry(hash)` else `firstEntry()` | Adding or removing a node moves only its neighbours' keys | Too few virtual nodes → skew |
| Search with filters (products, hotels) | **Inverted index** `Map<attribute=value, Set<id>>`, intersect starting from the smallest set, then sort with a `Comparator` chain | Filters become set intersections | Intersecting the largest set first; pagination stability |
| Hot key hammered by concurrent reads | **Single-flight** (`SingleFlight`) in front of the cache + jittered TTLs | 1,000 misses become 1 load | Caching failures; holding locks during the load |

## Money, ledgers, consistency

| Problem shape | Core approach | Why it fits | Watch out for |
|---|---|---|---|
| Splitwise: simplify debts | Net balance per user (they sum to 0) → **greedy with two max-heaps** (creditors, debtors): settle `min(credit, debt)` repeatedly → at most n−1 payments. O(n log n) | Only the net position matters, not who paid whom | Use `long` paise, not `double`; equal split remainder (give the extra paise to one person); EXACT must sum to the total, PERCENT to 100 |
| Wallet transfer / payments | **Idempotency key** (`IdempotencyStore`) + debit and credit under **locks on both accounts in sorted order** + an append-only ledger (double entry: every transfer writes −x and +x) | Retries must not repeat; A→B and B→A at once must not deadlock | Balance check outside the lock; deadlock; floating point |
| Flash sale / inventory | Atomic conditional decrement (`getAndUpdate(s -> s > 0 ? s - 1 : s)`) or per-SKU lock; cart holds with TTL | Oversell is a check-then-act race | Decrementing below zero; holds that never expire |
| Coupons / discount rules | **Chain of rules** (all must pass: expiry, min order, segment, usage limit) + **Strategy** per discount type; usage limit via atomic counter / `putIfAbsent(user+coupon)` | Rules vary independently and keep growing | Stacking order (percent before flat?); concurrent redemption beyond the limit |

## Structure and games

| Problem shape | Core approach | Why it fits | Watch out for |
|---|---|---|---|
| Workflow orchestrator / job dependencies | DAG + **Kahn's topological sort** (in-degree map + queue); run ready tasks on an executor; on completion decrement dependents' in-degree (atomically) and enqueue the ones that reach 0; fewer processed than total = cycle | Dependencies define a partial order | Cycles; concurrent in-degree updates; failure policy (skip downstream vs retry) |
| File system (mkdir, ls, cd, pwd) | **Composite**: `Directory` holds `TreeMap<String, Node>` (sorted `ls`); path resolution splits on `/`, handles `.`, `..`, absolute vs relative | Paths are a tree walk | `..` at root; a file and a directory with the same name; trailing slashes |
| Vending machine | **GoF State** (Idle → HasMoney → Dispensing); change: greedy for canonical coin sets (₹1, 2, 5, 10) with unlimited coins, **DP / backtracking over the coins actually available** when stock is limited | Same button behaves differently per state; greedy fails with limited stock | Can't make change → refund and abort atomically; inventory of 0 |
| Tic-tac-toe N×N | Counters `rows[n]`, `cols[n]`, `diag`, `antiDiag`: +1 for X, −1 for O; win when abs == n. O(1) per move | No board scan after each move | Anti-diagonal index (`r + c == n − 1`); occupied cell |
| Snake and ladder | `Map<Integer, Integer>` of jumps; `Deque` of players for turns; injectable dice (strategy) for tests | Board is just a jump table | Exact-landing rule on the last square; snake and ladder on the same cell; randomness in tests |
| Chess | Per-piece move **Strategy**; sliding pieces check the path is clear; a move is legal only if your king isn't in check afterwards (simulate, then undo via **Command**) | Movement rules vary per piece | Moving into check; castling, en passant, promotion as separate rules |
| Snake game | `Deque` for the body + `HashSet` of occupied cells | O(1) move and collision check | Moving into the cell the tail is leaving this turn |
| Text editor undo/redo | **Command** pattern with undo and redo stacks; a new command clears redo | Every action knows how to reverse itself | Forgetting to clear redo; grouping keystrokes |
| Logger | **Chain of Responsibility** by level + **Strategy** for appenders; async appender = `BlockingQueue` + consumer thread | Levels filter; destinations vary | Blocking the caller on I/O; losing logs on shutdown (drain) |
| In-memory pub-sub / message queue | Topic → partitions (append-only lists); `partition = hash(key) % n`; consumer groups keep an offset per partition; commit after processing (at-least-once) | Ordering per key, parallelism across partitions | Committing before processing loses messages; rebalancing |
| Notifications (email / SMS / push) | **Observer** for fan-out + **Factory/Strategy** per channel + `RetryPolicy` + dead-letter list; user preferences filter channels | Channels vary, events fan out | One slow channel blocking the others; duplicates on retry (idempotency key) |
| Chat / live updates | Connection registry (user → devices), presence via heartbeats, fan-out to online members, offline → unread + push (`ConnectionRegistry`) | Delivery depends on who is connected right now | Ordering per conversation (sequence per chat); duplicate sends (client message id) |
