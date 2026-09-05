# Changelog

All notable changes to RecoveryOS. Dates are approximate — this project was built in
compressed hackathon sprints, and the commit history tells the same story with hashes.

## [2.0.0] — RecoveryOS

### Renamed
- **Revenue Recovery Agent → RecoveryOS.** The old name described a retry bot; the
  product had outgrown it. "RecoveryOS" reflects what it actually is: a decision
  system, not a script.

### Added — the intelligence layer
- **Next-Best-Action engine** with full counterfactual simulation: every eligible
  transaction gets all valid candidate actions priced, not just the one we'd have picked.
- **Incremental net value optimization.** The engine subtracts discount cost, intervention
  cost, and a fatigue penalty from expected recovery — so a 90%-success discount can lose
  to a 75% payment link. There's a regression test pinned to exactly that scenario.
- **Recovery fatigue scoring.** After watching the early MVP spam reminders at customers
  who'd already ignored three, we added a fatigue score that suppresses customer-facing
  interventions band by band.
- **Confidence-gated automation.** ≥0.85 auto-executes, 0.60–0.85 safe actions only,
  <0.60 goes to the human review queue. Low-confidence autonomous actions burned us in
  the first demo run; this fixed it.
- **Human review queue, anomaly detection, recovery simulator, action performance lab,
  outcome memory, live decision stream (SSE).**

### Fixed (the bugs we actually hit)
- **OSIV lazy-loading crash** on `GET /api/recovery/transactions` — moved API responses
  to DTOs and set `spring.jpa.open-in-view=false` consistently. The Transactions page
  used to 500 on refresh; it doesn't anymore.
- **HIGH_VALUE bounds not actually enforced** — the segment was classified but never
  threaded into the decision call. Now: 5 retries, 25% discount ceiling, with tests.
- **Duplicate webhook deliveries double-charged** — added `eventId` with a DB unique
  index and an `existsByEventId` check (no more `findAll()` scans).
- **SSE vs REST batch divergence** — both paths now share one processing pipeline, so a
  transaction gets the same decision no matter how it entered the system.
- **`llmDriven=true` lies** — checkout/receivable proposals were flagged as LLM-generated
  whenever an API key existed even though they used heuristics. Now it's only true when
  a real LLM response shaped the decision, and the LLM never chooses actions — it only
  explains them.

## [1.x] — Revenue Recovery Agent (MVP)

The honest first version: rules engine + retry scheduling + reminders. It worked, but it
answered "which failed payment should we retry?" — the wrong question. Everything above
came from realizing the right question is "what is the next best recovery action by
incremental net revenue?"
