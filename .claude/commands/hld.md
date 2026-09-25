---
description: Draft the HLD from Design.txt and the code, using hld/TEMPLATE.md (written to hld/<package>.md)
argument-hint: <scale numbers: DAU, peak QPS, regions, latency targets>
---
Draft the HLD. Scale: $ARGUMENTS

Read `Design.txt`, the code in `src/main/java/com/lld/<PACKAGE>` and `hld/TEMPLATE.md`, then write
`hld/<PACKAGE>.md` following the template:
- Map each manager/aggregate to a deployable service: what it owns (its data) and what it never touches.
  The orchestrator becomes the API layer / BFF.
- Back-of-envelope from my numbers (read/write QPS, storage per year). Show the arithmetic.
- A datastore per service with a one-line justification and the access pattern it serves; name
  the indexes (one per repository finder) and the partition/shard key.
- Translate every in-process mechanism into its distributed form (AGENTS.md → HLD mode), and each
  applied pattern into its service-level counterpart where one exists (Observer → Kafka topic,
  Strategy → config-driven rules, Adapter → integration service).
- Kafka topics: name, producer, consumers, partition key, delivery semantics, idempotency.
- A Mermaid `flowchart LR` of the architecture and a Mermaid `sequenceDiagram` of the most
  contended flow.
Bullets only, since I'll talk over it rather than read it.
