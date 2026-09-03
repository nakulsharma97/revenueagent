# RecoveryOS — 5-Minute Screening Speech (Razorpay Build)

A complete, word-for-word script (~800 words ≈ 5 minutes). **Bold** lines are your talking points; *[bracketed italics]* are actions on screen. Read it, rehearse it once, then deliver it in your own words.

---

## Opening (0:00–0:20)

> **"Good morning. Most businesses quietly lose three to eight percent of their legitimate revenue to failed payments and abandoned checkouts. And the standard answer is: retry, then remind, then spam the customer. RecoveryOS is built on a completely different question. Not — which failed payment should I retry? But — for this customer, at this moment, what is the single intervention that creates the highest incremental net revenue? And it proves that answer with counterfactual simulation before a single action is ever taken."**

*[Command Center is on screen — stat cards visible.]*

## The problem (0:20–1:00)

> **"Normal recovery systems fail for four reasons. First, retry chains are blind — they retry, then remind, regardless of why the payment actually failed. Second, they optimize the wrong number: success probability, not incremental value. A discount with ninety percent success that gives away two thousand rupees of margin can be worth less than a payment link with seventy-five percent success and zero margin cost. Third, they spam — there's no concept of intervention fatigue, so they annoy the very customers they're trying to win back. And fourth, when AI is involved, it's a black box — nobody can explain why an action was chosen, and nobody can safely override it."**

## What RecoveryOS is (1:00–1:30)

> **"RecoveryOS is one deterministic intelligence engine covering three revenue sources: payment failures, abandoned checkouts, and B2B overdue invoices. For every eligible case, the engine walks a real pipeline. It reads the customer's recovery state and fatigue. It generates every action it's allowed to take — a silent retry, a scheduled retry, a payment link, discount tiers, a reminder, a payment plan, or escalation to a human. It simulates each one against a natural-recovery baseline. It subtracts the intervention cost, the discount cost, the fatigue penalty, and the risk penalty. And it executes the single action with the highest valid incremental net value — with a confidence score, and a human in the loop when confidence is low."**

> **"The key word is deterministic. Same input, same decision, on every path — REST, SSE streaming, the scheduler, the simulator. The demo is reproducible, and the reasoning is explainable."**

## The four things that make it different (1:30–2:30)

> **"First: counterfactual decisioning. Every decision stores the full set of actions it did NOT take, with their simulated success rates, costs, and net values — the selected one is highlighted. You can see exactly what was considered and why it lost.**

> **"Second: incremental value, not probability. The engine prices the lift over what the customer would have paid anyway. A discount for someone who was already going to pay is priced at nearly zero — we don't waste margin.**

> **"Third: fatigue and confidence, with a human in the loop. Every reminder raises a fatigue score. Past a threshold, we stop contacting the customer. Confidence below sixty percent, the last retry before a segment limit, an oversize discount, or a critical anomaly — all route to a human review queue with approve, override, or reject, fully audited.**

> **"And fourth: outcome memory — a learning loop. Every executed action is remembered as a source, failure-context, customer-segment, and action prior. So the system can literally tell you: for expired cards on high-value customers, payment links converted two out of three times. That's a clean training record for real ML later."**

## Live demo (2:30–4:00)

> **"Let me show you a real decision, deconstructed."**

*[Decision Ledger → open **Meera Iyer** — ₹15,999, INSUFFICIENT_FUNDS, HIGH_VALUE, 2 retries.]*

> **"This is a high-value customer. You can see the state chip, the fatigue chip, and the counterfactual bars — retry now, payment link, and discount tiers all the way up to twenty-five percent, because her segment allows a higher ceiling than a standard customer. And the decision trace shows every step: detection, state analysis, simulation, selection, execution, outcome. Nothing is hidden."**

*[Open **Aarav Mehta** — dead card.]*

> **"Here's the not-success-rate-only moment. This customer's card is dead. The engine simulated a discount — and rejected it, because a discount on a card that can never work is wasted margin. A payment link wins, because it lets the customer fix the method. That's incremental value thinking."**

*[Recovery Simulator — run STANDARD, flip to HIGH_VALUE.]*

> **"In the simulator you can flip the same case between segments and watch the allowed frontier change live — the ceiling, the tiers, the recommendation. And a fatigued customer case drops straight into stop-intervention and human review."**

*[Human Review — resolve one case.]*

> **"In the review queue, a human can approve, override, or reject — the audit trail records it, and the ledger marks it as a human override, never disguised as AI."**

*[Action Lab → Outcome Memory.]*

> **"And the Action Lab ranks every action by net value, not success rate — with the outcome memory below it showing what has actually worked per situation."**

## Engineering credibility (4:00–4:40)

> **"Under the hood: one decision pipeline, no duplicated logic. The REST batch, the SSE stream, the scheduler, the startup run and the simulator all call the same injected engine bean. Safety by construction — the rules engine re-validates every decision against hard bounds: retry limits, discount ceilings, cooldown, and idempotency enforced at the database level, so duplicate webhooks can never double-charge. And honest AI — the LLM never chooses. It only explains a decision after the engine made it, and we never claim otherwise: provenance is stamped on every attempt. The whole thing is Spring Boot and React, runs locally on an in-memory database, and seeds a realistic 320-item dataset with named demo cases and a held-out evaluation split."**

## Close (4:40–5:00)

> **"So, in a nutshell: counterfactuals instead of retries. Incremental value instead of probability. Fatigue-aware instead of spam. And human-safe instead of black-box. For Razorpay that means more recovered revenue, better customer experience, and an explainable, auditable decision layer that's ready to grow into real ML."**

> **"And it's built to plug straight into Razorpay's payment-intelligence stack — the engine doesn't care where the failed-payment event comes from."**

*[End on the Command Center screen.]*

---

## Everything about the project — at-a-glance cheat sheet

Use this to answer follow-ups or if the judges want "more about the system":

| Area | What's in it |
|---|---|
| **Product** | RecoveryOS — autonomous revenue-recovery intelligence (Razorpay Build, Track 03) |
| **Sources** | Payment failures (subscription dunning) · checkout abandonment · B2B overdue receivables |
| **Decision engine** | `NextBestActionEngine` — deterministic, Spring singleton; simulates every eligible action, ranks by incremental net value |
| **Formula** | DecisionScore = expectedIncrementalRecovery − interventionCost − discountCost − fatiguePenalty − riskPenalty |
| **Customer state** | NEW_FAILURE → SOFT_RISK → REPEATED_FAILURE → HIGH_VALUE_AT_RISK → RECOVERY_FATIGUE → STOP_INTERVENTION |
| **Fatigue** | 0–1 score from touches/failures; de-escalates contact; severe → stop or human |
| **Confidence policy** | ≥85% AUTO_EXECUTE · 60–85% SAFE_ACTION_ONLY · <60% HUMAN_REVIEW |
| **Human review** | approve / override / reject, reason recorded, audited, ledger provenance updated |
| **Counterfactuals** | `CounterfactualDecision` rows per case; selected row flagged; shown as bars in the ledger |
| **Outcome Memory** | (source × failure context × segment × action) priors; `GET /api/intelligence/outcome-memory` |
| **Action Lab** | actions ranked by net value; experiments declared separately |
| **Anomalies** | large failures, repeat failures, fatigue risk → HIGH/CRITICAL → review |
| **Bounds** | STANDARD 3 retries / 15% discount; HIGH_VALUE 5 retries / 25%; 60-min cooldown; ₹500 discount floor; live-editable at runtime |
| **Idempotency** | unique `eventId` DB constraint + indexed `existsByEventId` pre-check + SUCCESS-attempt skip |
| **Audit** | every pipeline event: ingest, evaluate, decide, execute, skip (cooldown/idempotent/control), batch start/end, review |
| **Evaluation** | ~20% held out (fixed seed), ~15% control group, metrics computed live from the ledger |
| **Data** | 320 seeded items + 10 named demo cases; H2 (dev) / MySQL (prod) |
| **Stack** | Spring Boot 3 / Java 21 · React + Vite · plain-Java engine; Claude API optional (explanation only) |
| **Docs** | `README.md` (architecture + principles) · `PROJECT_BRIEF.md` · `docs/DEMO_SCRIPT.md` (demo walkthrough) |

## Likely judge questions

| Question | Answer |
|---|---|
| Is this integrated with real Razorpay? | Ingestion is Razorpay-shaped (webhook `payment.failed` + `event_id`); execution uses explicit mocks so the demo runs offline and deterministically — swapping to live APIs is a thin adapter, not an architecture change. |
| Is the AI real? | The decision is a transparent deterministic economic engine (reproducibility is the point). The LLM is an optional explanation layer only; `llmDriven=true` strictly requires a real API response. Outcome rows are shaped for future ML. |
| How do you know it works? | Decisions stay inside hard policy bounds by construction; uplift/fatigue/confidence rules are unit-tested; REST-vs-SSE consistency, idempotency, audit and human-review routing are integration-tested. |
| Why not just retry harder? | Retry success decays; every customer-facing touch has a cost (fatigue, margin, risk). The engine maximizes net incremental value, not attempts. |
| What's the business value? | Incremental recovered revenue minus intervention cost — quantified per action and per situation in the Action Lab and Outcome Memory. |

## Demo fail-safes

- Case missing from the ledger? Click **Run Batch ▶** once — it re-processes eligible items and refreshes everything.
- Numbers question? Quote **decisions**, not ₹ totals (outcome draws are seeded but vary per run).
- Sanity endpoints: `GET /api/intelligence/command-center` · `GET /api/intelligence/review` · `GET /api/intelligence/outcome-memory` · `GET /api/recovery/attempts` · `GET /api/audit`.