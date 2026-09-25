---
description: Draft the HLD from this repo's code using the HLD template (written to .claude/harness/HLD.md)
argument-hint: <scale numbers: DAU, peak QPS, regions, latency targets>
---
Draft the high-level design for this system. Scale: $ARGUMENTS

Read the main code, `.claude/harness/REPO_NOTES.md` and `.claude/harness/HLD-TEMPLATE.md`, then
write `.claude/harness/HLD.md` (git-excluded, not part of the solution) following the template:
- Map each main class/aggregate in this repo to a deployable service, with what it owns.
- Back-of-envelope from my numbers, with the arithmetic shown.
- A datastore per service with a one-line justification, the indexes, and the shard key.
- Translate every in-process concurrency mechanism in the code to its distributed form
  (lock → row lock / conditional update / Redis lock with fencing token; in-memory events →
  Kafka topic with a partition key; in-memory holds → Redis `SET NX PX` or a held_until column).
- Kafka topics: producer, consumers, partition key, delivery semantics, idempotency.
- A Mermaid `flowchart LR` of the architecture and a `sequenceDiagram` of the most contended flow.
Bullets only; I'll talk over it.
