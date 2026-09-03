# DEMO SCRIPT — RecoveryOS

A ~4-minute walkthrough for judges. Deterministic decisions, seeded data, H2 in-memory.

**Setup**: `cd backend && mvn spring-boot:run`, then `cd frontend && npm install && npm run dev`. Open `http://localhost:5173`. The backend auto-seeds 320 items + 10 named demo cases and auto-runs the first batch, so the dashboard is already live.

> Metrics *numbers* vary a little between runs (outcome draws are seeded but random); **decisions are deterministic**. Quote decisions, not ₹ totals.

---

## 1. Command Center (0:00–0:30)

- Headline stat band: **Revenue at Risk, AI Decisions Today, Human Escalations, Open Anomalies, Outcomes Recorded, Active Experiments, Fatigue Alerts**.
- Say: *“Before any action runs, RecoveryOS prices every option as incremental net revenue — not just ‘will they pay’, but ‘would they have paid anyway’.”*
- Point at the **LedgerTape** scrolling live decisions.

## 2. A single decision, deconstructed (0:30–1:20)

In **Decision Ledger**, open the row for **Meera Iyer** (₹15,999, INSUFFICIENT_FUNDS, HIGH_VALUE, 2 retries). In the case file:

1. **STATE chip** — `HIGH_VALUE_AT_RISK`; **FATIGUE chip**.
2. **Counterfactual simulation** — show the ranked alternatives with net values: retry now ₹X, pay-link ₹Y, **5/10/15/20/25% discount tiers** — the 20–25% tiers exist only because the segment ceiling is 25% (a STANDARD customer would never see them). Selected row highlighted.
3. **Decision trace** — DETECTION → INTELLIGENCE (state+fatigue) → SIMULATION (N candidates vs natural baseline) → SELECTION (next best action, confidence, policy) → EXECUTION → OUTCOME.
4. **Recovery Timeline** below — every attempt on this case, oldest → newest.

Then open **Aarav Mehta** (dead card, ₹4,999): the engine *simulated* a 10–15% discount and rejected it — the customer must replace their card, so the **pay-link** wins on incremental value. This is the “not success-rate-only” moment.

## 3. Recovery Simulator (1:20–2:00)

Open **Recovery Simulator**. Run one case as **STANDARD**, then flip the same case to **HIGH_VALUE** and re-run:
- same amount/cause → different discount ceiling; show the counterfactual bars change.
Then a fatigue case (4 retries, low reliability): state → `STOP_INTERVENTION`/escaped to human review; confidence drops below 60% → policy **HUMAN_REVIEW**.
Say: *“Every number here is a deterministic counterfactual — same input, same answer, on REST, SSE, startup and scheduler paths alike.”*

## 4. Human Review Queue (2:00–2:40)

Open **Human Review**. Cases present because: sign-off thresholds (final retry before the segment limit), low engine confidence, and HIGH anomalies (Zoya Khan ₹1,50,000). Resolve one live:
- **Approve** the AI action — the linked attempt flips to APPROVED and the audit trail records `REVIEW_CASE_RESOLVED` (see Alerts/Audit API).
- Explain the Open Anomalies table.

## 5. Action Lab + learning loop (2:40–3:20)

Open **Action Lab**: actions ranked by **net value** (recovered − intervention cost), with success rate as context — e.g. a cheap reminder or plan may outrank a margin-burning discount. Below it, the **Outcome Memory** table (`/api/intelligence/outcome-memory`): the same action remembered *per situation* — “for CARD_EXPIRED in HIGH_VALUE, pay-links converted 2/3 for ₹X net; for INSUFFICIENT_FUNDS, the 10% discount burned margin”. Then the declared **Experiments** (pay-link vs reminder, discount sensitivity, payment-plan adoption) with their control percentages — and an inline “declare experiment” form.

## 6. Why this is not a normal recovery system (3:20–4:00)

Three one-liners while on Command Center:

1. **Counterfactuals, not retries.** Every decision stores the full set of actions it *didn't* take and why.
2. **Incremental value, not probability.** The engine refuses to spend margin where the customer would pay anyway (discounts on transient network failures are priced near zero).
3. **Bounded, explainable, human-safe.** Confidence floors, fatigue suppression, segment ceilings and review cases keep the machine inside policy — and the optional LLM only ever explains, never chooses.

Then answer the obvious question: *why did it do that?* → every decision carries the reasoning + top factors in plain English.

---

## Script notes for judges

- If a case you search isn't in the ledger, run a batch first (top-right **Run Batch ▶**) — it re-processes eligible items and refreshes everything, including the review queue.
- Backend sanity endpoints: `GET /api/intelligence/command-center`, `GET /api/intelligence/review`, `GET /api/intelligence/outcome-memory`, `GET /api/recovery/attempts`, `GET /api/audit/events` (audit trail).
