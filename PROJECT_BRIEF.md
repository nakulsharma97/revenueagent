# Project Brief — AI Revenue Recovery Agent
**Razorpay AI Buildathon · Track 03: AI Revenue Recovery**

This is the systematic spec the implementation follows. Written so it can also be handed to any AI coding tool (Claude Code, Cursor, etc.) as a standalone build prompt.

---

## 1. Problem statement (from the official track brief)

> Build an agent that detects revenue at risk, determines the right intervention, and executes a bounded recovery workflow — from payment failures and checkout abandonment to overdue receivables.
>
> **The bar:** Don't just identify the problem. Show measured money recovered across a batch, with honest metrics (including the cost of each intervention).

Three words carry the grading weight, and the design below is built around them:
- **"Detects"** → a real signal pipeline, not a hardcoded list.
- **"Bounded"** → the AI never acts outside hard limits a human set — this is what separates an "agent" from "an LLM with API access."
- **"Measured"** → a revenue-recovered number that is defensible, not decorative (recovered minus what it cost to recover it).

## 2. Scope decision

Of the six example directions (checkout drop-off, failed-subscription recovery, B2B receivables, mandate retry, Hinglish voice recovery, promise-to-pay tracker), this build picks **one deep, well-scoped vertical slice** rather than a shallow pass at all six:

**Primary: Failed-subscription payment recovery (dunning management).**
Rationale: it has a clean, closed-loop, measurable unit (one failed transaction → one resolved outcome), realistic synthetic data is easy to generate credibly, and it maps directly onto the requester's existing Spring Boot + MySQL skill set — no unfamiliar ML training pipeline required to hit the bar.

Checkout-abandonment recovery is architecturally identical (same detect → diagnose → decide → execute → measure loop) and is left as a same-pattern extension, not built by default, to keep the submission focused rather than shallow.

## 3. Non-negotiable constraints ("bounded workflow")

The agent must never act outside these, regardless of what the LLM proposes:

| Rule | Limit |
|---|---|
| Max retry attempts per transaction | 3 |
| Cooldown between retries | 60 minutes |
| Max discount the agent can offer | 15% |
| Minimum transaction amount eligible for a discount | ₹500 |
| Actions requiring human sign-off | Anything above the discount ceiling, or a 3rd consecutive failure |

These are enforced in plain Java (`RulesEngine`), evaluated **before** any LLM output is allowed to execute. The LLM proposes; the rules engine disposes.

## 4. System design

```
Synthetic transaction feed (seeded, realistic decline codes)
        │
        ▼
 [1] Detection        — is this transaction at risk? (status = failed/past-due)
        │
        ▼
 [2] Diagnosis         — classify failure_reason → retryable vs terminal
        │
        ▼
 [3] Decision Agent     — RulesEngine (hard bounds) → LLM (reasoning + choice) → bounds re-check
        │
        ▼
 [4] Execution          — mock payment gateway / mock notification service
        │
        ▼
 [5] Metrics Ledger     — recovered ₹, intervention cost, net recovered, recovery rate
        │
        ▼
 [6] Dashboard (React)  — funnel, revenue chart, per-transaction reasoning trace
```

### Why the LLM sits *inside* a rules boundary, not in front of it
A judge scoring "strictly defense-only, bounded workflow" is explicitly checking that the AI cannot freelance. Architecturally: `RulesEngine.eligibleActions(transaction)` returns an allow-list; the LLM can only pick **from** that allow-list and justify the pick — it cannot invent a 7th retry or a 40% discount. This is the single most important design decision in the project and should be the first thing said out loud in the demo.

## 5. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Backend | Spring Boot 3 / Java 21 | Existing strength; `@Scheduled` reused for retry timing |
| DB | MySQL (H2 in-memory for zero-setup local demo) | Relational fit for ledger-style data |
| Decision layer | Rules engine (plain Java) + Claude API (`claude-sonnet-4-6`) for reasoning/message drafting | Keeps "AI" bounded and explainable, not a black box |
| Frontend | React + Vite, Recharts | Matches existing stack; live charts for the "measured" requirement |
| Mock services | Hand-rolled `MockPaymentGatewayService`, `MockNotificationService` | No real Razorpay integration needed for a buildathon demo — judges care about the decision logic, not gateway plumbing |

## 6. Data model

`customers → subscriptions → transactions → recovery_attempts`, plus a derived `metrics_ledger` view. Full schema and entities are in `backend/src/main/java/.../model/`.

## 7. What "measured" means here — the metrics that must appear in the demo

- Total transactions at risk (batch size)
- Revenue recovered (₹)
- Recovery rate (%)
- Total intervention cost (discounts given + messaging cost)
- **Net revenue recovered = recovered − cost** ← the number that actually answers the brief
- Baseline comparison: net recovered *with* the agent vs. a naive "retry everything once" baseline — this single chart is the strongest evidence of value in a 3-minute judge walkthrough

## 8. Build order (time-boxed)

1. Schema + synthetic seed data (200–500 transactions, realistic decline-code distribution)
2. `RulesEngine` — works end-to-end with zero AI dependency (fallback path; de-risks the demo if the LLM API is unreachable on stage)
3. `DecisionAgentService` — LLM call layered on top, same interface, feature-flagged
4. Mock execution + outcome simulation
5. Metrics aggregation + dashboard
6. Polish: reasoning-trace UI, baseline-comparison chart

## 9. Demo script (for the judging bar)

1. Show the dashboard with 300 seeded at-risk transactions — stat cards show the batch size, recovery rate, and net revenue.
2. Click **Run recovery batch** — watch the ledger tape populate, stat cards update, and the decision table fill with the agent's per-transaction reasoning.
3. Click any row in the decision table to expand the agent's one-line reasoning for that transaction — e.g. "Rules-only mode: first failure on a retryable decline code — retry immediately."
4. End on the net-recovered-vs-baseline chart — the headline number: ₹4,11,075 net recovered by the agent vs ₹3,02,365 naive baseline, same batch.
