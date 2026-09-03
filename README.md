# RecoveryOS — Autonomous Revenue Recovery Intelligence

**RecoveryOS** (previously *Revenue Recovery Agent*) is an autonomous revenue-recovery intelligence system. It does not ask *"which failed payment should I retry?"* — it answers *"for this customer, at this moment, what is the NEXT BEST RECOVERY ACTION that creates the highest **incremental** net revenue?"* and proves the answer with counterfactual simulation.

Built for the Razorpay AI Buildathon (Track 03).

<!-- TODO: Record a 90-second screen capture, save as docs/demo.gif + docs/demo-thumbnail.png,
     then uncomment the line below. See docs/DEMO_SCRIPT.md for the walkthrough outline. -->
<!-- [![Demo](docs/demo-thumbnail.png)](docs/demo.gif) -->

## What this does

The system runs a **detect → diagnose → simulate → select → execute → learn** loop across three revenue sources: **payment failures** (subscription dunning), **checkout abandonment** (cart recovery), and **B2B overdue receivables** (invoice recovery). For every eligible item the **Recovery Intelligence Engine** (package `com.razorpay.recovery.intelligence`) counterfactually simulates *every* bounded action — retry now / retry later / silent retry / payment link / 5–25% discount tiers / reminder / payment plan / escalation — scores each by expected **net** value (recovered − discount cost − intervention cost − risk penalty) over a natural-recovery baseline, and executes the winner. `RulesEngine` hard bounds and uplift/fatigue policies are enforced *before* anything runs; a live LLM (optional) only ever explains a structured decision, never chooses one. Every decision, outcome and review action is persisted for the Action Performance Lab and outcome learning.

## Architecture

```
Feature vector → Customer state + fatigue → Counterfactual simulation of every eligible action
  → expected net value ranking → confidence band → automation policy → NEXT BEST ACTION
  → bounded execution → outcome recorded → learning
```

```
backend/   Spring Boot 3 (Java 21) — intelligence engine, rules engine, mock execution, metrics, audit
frontend/  React + Vite — Command Center, Simulator, Human Review, Action Lab + live dashboards
```

### The Recovery Intelligence layer (new)

| Concept | Where | What it proves |
|---|---|---|
| Next-Best-Action engine | `intelligence/NextBestActionEngine.java` | simulates every eligible action and picks the highest **incremental net value** |
| Multi-action uplift scoring | `intelligence/UpliftScoringService.java` | per-action success vs. natural baseline; discounts are not wasted on sure-things or transient failures |
| Value optimizer | `intelligence/RecoveryValueOptimizer.java` | cost/discount/risk-aware selection, never success-rate-only |
| Customer recovery state | `intelligence/CustomerStateService.java`, `RecoveryState.java` | NEW_FAILURE → REPEATED_FAILURE → RECOVERY_FATIGUE → STOP_INTERVENTION |
| Recovery fatigue engine | `intelligence/RecoveryFatigueService.java` | suppresses contact as fatigue rises; severe fatigue stops automation |
| Decision confidence | `intelligence/DecisionConfidenceService.java` | <60% → HUMAN_REVIEW, ≥85% → AUTO_EXECUTE |
| Counterfactual decisions (persisted) | `intelligence/CounterfactualDecision.java` | every simulation row is stored; the selected row is flagged |
| Outcome learning | `intelligence/RecoveryOutcome.java`, `OutcomeLearningService.java` | per-action success/net-value stats feed the Action Lab |
| Human review queue | `intelligence/HumanReviewCase.java` | approve / override / reject with audit trail |
| Anomaly detection | `intelligence/AnomalyDetectionService.java` | large failures, repeat failures, fatigue risk → HIGH/CRITICAL route to review |
| Experimentation policy | `intelligence/RecoveryExperiment.java` | declared control/treatment policies, kept out of per-item decisions |
| Recovery Timeline | `GET /api/intelligence/timeline` | every attempt on one entity, oldest → newest |


| What it proves | File |
|---|---|
| Bounded workflow (hard limits enforced before any engine output) | `backend/.../recovery/RulesEngine.java` |
| Decision layer: engine-driven action + optional LLM explanation | `backend/.../recovery/DecisionAgentService.java` |
| End-to-end loop across all 3 revenue sources | `backend/.../recovery/RecoveryOrchestratorService.java` |
| Honest metrics (recovered − intervention cost) | `backend/.../metrics/MetricsService.java` |
| Realistic synthetic batch (320 items auto-seeded on startup) | `backend/.../config/DataSeeder.java` |
| Human sign-off queue | `backend/.../recovery/RecoveryController.java` (`GET /pending-review`) |
| Live bounds editor (runtime config changes) | `backend/.../config/ConfigController.java` (`PUT /config/bounds`) |
| Mutable bounds configuration | `backend/.../config/BoundsConfig.java` |
| Silent-first recovery (RETRY_SILENT) | `RulesEngine.eligibleActions()` — first-attempt retryable failures use background retry only |
| Customer segment-aware bounds | `BoundsConfig.boundsFor(segment)` — HIGH_VALUE gets wider limits |
| DSO metric (B2B KPI) | `MetricsService.currentMetrics()` — Days Sales Outstanding for receivables |
| Promise-to-pay tracker | `Receivable.promiseStatus` — tracks kept/broken promises |

## Non-negotiable constraints

Pulled from `application.properties` → `BoundsConfig.java` → `RulesEngine.java`:

| Rule | Default limit |
|---|---|
| Max retry attempts per item | 3 (STANDARD) · 5 (HIGH_VALUE) |
| Cooldown between retries | 60 minutes |
| Max discount the agent can offer | 15% (STANDARD) · 25% (HIGH_VALUE) |
| Minimum amount eligible for a discount | ₹500 |
| Actions requiring human sign-off | Discount above the segment's ceiling, or the last retry before the segment's limit |
| **Idempotency guarantee** | **DB unique constraint on `eventId` + application-level pre-check; second batch run skips already-recovered items** |
| **Held-out evaluation split** | **20% of each entity type is held out (random, fixed seed); agent never sees these items; held-out metrics reported separately** |

These are enforced in plain Java (`RulesEngine`), evaluated **before** any LLM output is allowed to execute. The bounds are changeable at runtime via `PUT /api/config/bounds` — no server restart needed.

**Idempotency** is enforced at two layers: (1) a `UNIQUE` database constraint on each entity's `eventId` field, which physically rejects duplicates at the storage level, and (2) an application-level pre-check in `RecoveryOrchestratorService` that skips any entity with an existing `SUCCESS` recovery attempt, preventing double-charges and double-counted metrics.

**Held-out split** uses a `boolean isHeldOut` field on every entity, populated by `DataSeeder` with a fixed random seed (~20% per source type, reproducible across runs). This field is never read by `DecisionAgentService` or `RulesEngine` — it is purely a post-hoc label used only by `MetricsService` when computing `?scope=held-out` metrics, making recovery-rate claims credible on unseen data.

## Predictable demo scenarios

`DataSeeder` additionally seeds ten **named** cases so a demo is predictable. Open the Decision Ledger and search by customer name:

| Case | Seed | Expected next-best action |
|---|---|---|
| Riya Sharma (self-heal) | ₹9,999 · NETWORK_ERROR · reliability 0.88 | RETRY_SILENT — state LIKELY_TO_SELF_RECOVER, no customer contact |
| Aarav Mehta (dead card) | ₹4,999 · CARD_EXPIRED · 1 retry | SEND_PAYMENT_LINK (discount simulated but rejected) |
| Kabir Nair (retry window) | ₹2,000 · UPI_TIMEOUT · 1 retry | RETRY_NOW |
| Meera Iyer (high value) | ₹15,999 · INSUFFICIENT_FUNDS · 2 retries · HIGH_VALUE | OFFER_DISCOUNT up to 20% (25% ceiling, > the 15% STANDARD cap) |
| Dev Rao (final attempt) | ₹899 · UPI_PIN_MISMATCH · 2 retries | RETRY_SCHEDULED flagged for human sign-off |
| Zoya Khan (big ticket) | ₹1,50,000 · CARD_STOLEN_FLAG | anomaly HIGH → review case |
| Kavya Singh (price-hesitant cart) | ₹24,999 checkout · PRICE_HESITATION | OFFER_DISCOUNT (15%) |
| Nikhil Verma (distracted cart) | ₹9,999 checkout · DISTRACTED | SEND_PAYMENT_LINK |
| Metro Logistics | ₹2,50,000 invoice · 60 days overdue | OFFER_PAYMENT_PLAN |
| GreenLeaf Foods | ₹85,000 invoice · broken promise, 10 days | PROMISE_FOLLOWUP |

Expected *decisions* are deterministic (the engine uses no randomness); *outcomes* follow the mocked conversion model, so recovered totals vary per run while decisions do not.

## New API surface

| Endpoint | Purpose |
|---|---|
| `POST /api/intelligence/simulate` | Recovery Simulator — returns state, fatigue, counterfactual ranking, chosen action |
| `GET /api/intelligence/command-center` | Command Center aggregates |
| `GET /api/intelligence/counterfactuals?sourceType=&id=` | persisted simulation rows for a case |
| `GET /api/intelligence/timeline?sourceType=&id=` | recovery timeline of a case |
| `GET /api/intelligence/review` · `POST /api/intelligence/review/{id}/resolve` | human review queue + approve/override/reject |
| `GET /api/intelligence/anomalies` | anomaly feed |
| `GET /api/intelligence/experiments` · `POST /api/intelligence/experiments` | experimentation policies |
| `GET /api/intelligence/action-performance` | Action Performance Lab (ranked by net value) |

## Running it locally

### Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` with an in-memory H2 database — **no setup required**. `DataSeeder` seeds 200 payment failures + 80 checkout abandonments + 40 overdue receivables (320 items total) and auto-runs a recovery batch on startup, so the dashboard shows real data immediately.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`. Automatically connects to the backend at `localhost:8080` — no `.env` file needed for local dev.

### Optional: Enable live LLM reasoning

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export LLM_ENABLED=true
cd backend && mvn spring-boot:run
```

Without these, `DecisionAgentService` falls back to a deterministic heuristic path — the demo never breaks.

## Results — how to read the demo numbers

The seeded dataset and every mock outcome are **deterministic** (all `Random` instances are fixed-seed `42`), so a fresh run of the same code on the same database state reproduces the same decisions and the same metrics. The exact headline figures (recovery rate, net revenue recovered, uplift deltas) are **computed live by the backend** from the actual batch, so the numbers on the dashboard are always internally consistent — do not hardcode them into a script.

What the seeded run guarantees (structural, not random):

- **320 items auto-seeded** on startup: 200 payment failures + 80 abandoned checkouts + 40 overdue receivables. A recovery batch auto-runs so the dashboard is populated on first load.
- **~20% of each source is held out** (evaluation-only, never touched by the agent's tuning) and **~15% is a control group** that receives no agent intervention — both reproducible from the fixed seed.
- **~Top 20% by transaction value are HIGH_VALUE customers** with wider bounds: 5 retries / 25% discount ceiling vs 3 retries / 15% for STANDARD.
- **Silent-first recovery:** first-attempt retryable payment failures get `RETRY_SILENT` (zero customer contact); customer-facing actions only open up after the silent path is exhausted.
- **B2B receivables KPIs** (DSO, average days overdue, promise-keep rate) and the **held-out subset metrics** are shown live in the Reports page.
- **Agent vs baseline** and **uplift analysis (control vs treatment by segment)** are computed from the actual attempt ledger at runtime.

Run the app, click **Run Batch**, and read the cards/charts for the current figures — the **Net recovered vs baseline** chart is the headline number, and the **Reports → Uplift Analysis** table shows which segment actually benefits from intervention.

## Uplift-aware targeting

Not every recovered rupee required the agent's help — some customers would have paid anyway, and overspending discounts on them is wasted margin. This project implements a lightweight uplift-aware targeting layer on top of the bounded-action pipeline.

**How it works:** A held-out control group (~15% of all entities) receives zero agent intervention. Their recovery rate establishes the natural-recovery baseline — what would have happened without the agent. Every remaining entity is classified into one of four causal-response segments using a heuristic model built from features already available on the entity (reliability score, failure reason, retry history, amount):

| Segment | Rule | What it means |
|---|---|---|
| **SURE_THING** | High reliability + soft failure (network error, bank timeout) | Would recover anyway — discounts are wasted margin |
| **PERSUADABLE** | Default — the majority case | Intervention genuinely changes the outcome |
| **DO_NOT_DISTURB** | Failed-once + small amount | A message/discount on a low-value, already-failed case risks annoyance for little upside |
| **LOST_CAUSE** | Low reliability + hard decline (card stolen, invalid CVV) | No intervention meaningfully changes a hard, non-retryable decline |

The `RulesEngine` enforces this in code: SURE_THING and LOST_CAUSE entities have `OFFER_DISCOUNT` removed from their eligible action set; DO_NOT_DISTURB entities have both `SEND_PAYMENT_LINK` and `OFFER_DISCOUNT` removed. This is a real tightening of the bounded-action set, not just a label.

**Where to see it live:** Reports → **Uplift Analysis** shows the control-vs-treatment recovery rates and the per-segment delta (Δ) computed from the actual attempt ledger after each batch. The expected shape, from the probability models above: PERSUADABLE shows the largest positive delta (intervention clearly helps), SURE_THING and LOST_CAUSE deltas are near zero (intervention adds little), and DO_NOT_DISTURB can be slightly negative — evidence that the silent-only policy is correct for that segment. The precise percentages are computed at runtime from the deterministic seed; they are always visible on the dashboard rather than asserted in prose.

*Based on uplift modeling / conditional average treatment effect (CATE) estimation, an established technique in causal inference for targeting interventions — used in production by companies like Criteo for ad targeting and studied extensively for churn/retention use cases.*

## Using it

1. Start the backend — 320 at-risk items are already seeded and a batch auto-runs.
2. Start the frontend, open it, click **Run Batch**.
3. Watch the stat cards, charts, and decision ledger populate.
4. The **Agent vs. Baseline** chart is the headline number.
5. Click any row in the decision ledger to see the agent's reasoning and full decision trace.
6. Check **Alerts** for items requiring human sign-off.
7. Use **Settings** to change recovery bounds at runtime and re-run.

## API endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/metrics` | Combined metrics across all 3 sources |
| GET | `/api/metrics?scope=held-out` | Held-out-only metrics (20% unseen split) |
| GET | `/api/metrics/dashboard` | Metrics + funnel + actions + efficiency in one response |
| GET | `/api/metrics/funnel` | Recovery pipeline status distribution |
| GET | `/api/metrics/actions` | Per-action success rate breakdown |
| GET | `/api/metrics/efficiency` | Per-action ROI (recovered per rupee spent) |
| GET | `/api/metrics/batches` | Per-batch metrics history |
| GET | `/api/metrics/simulate?maxRetries=X&maxDiscountPercent=Y` | What-if simulator (projects impact of bounds changes) |
| GET | `/api/metrics/uplift` | Uplift analysis: control vs treatment recovery by segment |
| POST | `/api/recovery/run-batch` | Execute a recovery batch |
| GET | `/api/recovery/transactions` | All seeded transactions (DTOs — safe with OSIV off) |
| GET | `/api/recovery/receivables` | All receivables with promise-to-pay data |
| GET | `/api/recovery/attempts` | Persisted recovery attempts, newest first (survives page refresh) |
| GET | `/api/recovery/attempts/{id}/trace` | Full decision trace for one attempt |
| GET | `/api/recovery/pending-review` | Items requiring human sign-off |
| PUT | `/api/recovery/attempts/{id}/signoff` | Approve/reject a human sign-off request |
| GET | `/api/recovery/export` | CSV export of all attempts |
| POST | `/api/webhooks/razorpay/payment-failed` | Razorpay webhook ingestion (idempotent via eventId) |
| GET | `/api/recovery/run-batch/stream` | SSE streaming batch (total → attempt events → done counts) |
| GET | `/api/audit` | Audit trail (batch/decision/execution events) |
| GET | `/api/config/bounds` | Current recovery bounds (incl. HV bounds) |
| PUT | `/api/config/bounds` | Update bounds at runtime (incl. HV bounds) |

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3 / Java 21 |
| Database | H2 in-memory (dev) / MySQL (prod) |
| Decision | RulesEngine (plain Java) + Claude API (optional) |
| Frontend | React + Vite, Recharts |
| Mock services | MockPaymentGatewayService, MockNotificationService |

## Demo script (3-minute walkthrough)

1. **Show the Bounds Register** — the hard limits that make this "an agent, not an LLM with API access"
2. **Click into one transaction** — show the rules engine narrowing to eligible actions, then the agent's reasoning
3. **Run the batch** — watch the charts populate with real numbers
4. **Show Agent vs. Baseline** — the headline chart proving measured value
5. **Check Alerts** — show items requiring human sign-off (bounded workflow proof)
6. **Change a bound live** — lower the discount ceiling in Settings, re-run, show more sign-offs

---

**Full project brief:** See `PROJECT_BRIEF.md` for scope decisions, design rationale, and the complete problem breakdown.
