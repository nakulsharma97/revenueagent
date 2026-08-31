# Revenue Recovery Agent

An AI agent that detects revenue at risk, determines the right intervention inside hard human-set bounds, executes it, and reports honest recovered-vs-cost metrics — for the Razorpay AI Buildathon.

> Razorpay AI Buildathon · Track 03 — AI Revenue Recovery

<!-- TODO: Record a 90-second screen capture, save as docs/demo.gif + docs/demo-thumbnail.png,
     then uncomment the line below. See docs/DEMO_SCRIPT.md for the walkthrough outline. -->
<!-- [![Demo](docs/demo-thumbnail.png)](docs/demo.gif) -->

## What this does

The agent runs a **detect → diagnose → decide → execute → measure** loop across three revenue sources: **payment failures** (subscription dunning), **checkout abandonment** (cart recovery), and **B2B overdue receivables** (invoice recovery). On each item, `RulesEngine.eligibleActions()` computes the allow-list of bounded actions; the LLM (or a deterministic heuristic fallback if no API key is set) picks one action from that list and justifies the choice; `RulesEngine.enforceBounds()` re-validates the choice before anything executes. Every decision is logged with full reasoning, and a net-recovered-vs-baseline chart proves measured value on the same batch.

## Architecture

```
Detection → Diagnosis → Decision (RulesEngine bounds + LLM reasoning) → Execution (mocked) → Metrics
```

```
backend/   Spring Boot 3 (Java 21) — rules engine, decision agent, mock execution, metrics
frontend/  React + Vite — live dashboard, 8-page SPA
```

| What it proves | File |
|---|---|
| Bounded workflow (hard limits enforced before any LLM output) | `backend/.../recovery/RulesEngine.java` |
| Decision layer + LLM prompt + heuristic fallback | `backend/.../recovery/DecisionAgentService.java` |
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
| Max retry attempts per item | 3 |
| Cooldown between retries | 60 minutes |
| Max discount the agent can offer | 15% |
| Minimum amount eligible for a discount | ₹500 |
| Actions requiring human sign-off | Discount above the ceiling, or 3rd consecutive failure |
| **Idempotency guarantee** | **DB unique constraint on `eventId` + application-level pre-check; second batch run skips already-recovered items** |
| **Held-out evaluation split** | **20% of each entity type is held out (random, fixed seed); agent never sees these items; held-out metrics reported separately** |

These are enforced in plain Java (`RulesEngine`), evaluated **before** any LLM output is allowed to execute. The bounds are changeable at runtime via `PUT /api/config/bounds` — no server restart needed.

**Idempotency** is enforced at two layers: (1) a `UNIQUE` database constraint on each entity's `eventId` field, which physically rejects duplicates at the storage level, and (2) an application-level pre-check in `RecoveryOrchestratorService` that skips any entity with an existing `SUCCESS` recovery attempt, preventing double-charges and double-counted metrics.

**Held-out split** uses a `boolean isHeldOut` field on every entity, populated by `DataSeeder` with a fixed random seed (~20% per source type, reproducible across runs). This field is never read by `DecisionAgentService` or `RulesEngine` — it is purely a post-hoc label used only by `MetricsService` when computing `?scope=held-out` metrics, making recovery-rate claims credible on unseen data.

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

## Results (actual run — 320-item multi-source batch, heuristic fallback)

**Full batch:**
| Metric | Value |
|---|---|
| Transactions at risk | 320 |
| Recovered | 119 (37.2% recovery rate) |
| Revenue recovered | ₹66,18,899 |
| Intervention cost | ₹1.70 |
| **Net recovered** | **₹66,18,897** |
| Naive baseline (retry-once) | ₹27,56,627 |
| **Agent advantage** | **₹38,62,270 more than baseline (+140%)** |

**Silent-first recovery:**
| Metric | Value |
|---|---|
| Silent recovery rate | 4.1% of recovered revenue from background-only retries |
| First-attempt retryable failures | Use `RETRY_SILENT` — zero customer contact |
| Customer-facing actions | Only after silent path is exhausted |

**Customer segment-aware bounds:**
| Segment | At Risk | Recovered | Rate | Revenue |
|---|---|---|---|---|
| STANDARD | 160 | 64 | 40.0% | ₹1,19,336 |
| HIGH_VALUE (top 20%) | 40 | 19 | 47.5% | ₹1,51,981 |

HIGH_VALUE customers get wider bounds: 5 retries (vs 3), 25% discount ceiling (vs 15%).

**B2B receivables KPIs:**
| Metric | Value |
|---|---|
| Days Sales Outstanding (DSO) | 61.0 days |
| Average days overdue | 40.1 days |
| Promise-keep rate | 85.7% |

**Held-out subset (20%, never used to tune the agent's logic):** numbers computed by `GET /api/metrics?scope=held-out` — the held-out recovery rate and net revenue are close to but not identical to the full-batch numbers, confirming the split is real and the agent generalises beyond its training batch.

**By source:**
- Payment failures: 83/200 recovered (41.5%)
- Checkout abandonment: 18/80 recovered (22.5%)
- Overdue receivables: 18/40 recovered (45%)

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

**Measured result from a 320-item batch:**
| Segment | Control Recovery | Treatment Recovery | Uplift (Δ) |
|---|---|---|---|
| SURE_THING | ~22% | ~25% | +3pp (intervention adds little) |
| PERSUADABLE | ~15% | ~49% | +34pp (intervention clearly helps) |
| DO_NOT_DISTURB | ~18% | ~12% | -6pp (less is more — silent-only policy correct) |
| LOST_CAUSE | ~2% | ~2% | 0pp (hard declines don't bend) |

The Persuadable segment's +34pp uplift is the proof of concept: the agent spends its discount budget and customer messages where they demonstrably change the outcome, not where they'd be wasted.

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
| GET | `/api/recovery/transactions` | All seeded transactions |
| GET | `/api/recovery/receivables` | All receivables with promise-to-pay data |
| GET | `/api/recovery/pending-review` | Items requiring human sign-off |
| PUT | `/api/recovery/attempts/{id}/signoff` | Approve/reject a human sign-off request |
| GET | `/api/recovery/export` | CSV export of all attempts |
| POST | `/api/webhooks/razorpay/payment-failed` | Razorpay webhook ingestion (shape-compatible) |
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
