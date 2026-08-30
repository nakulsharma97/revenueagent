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

## Non-negotiable constraints

Pulled from `application.properties` → `BoundsConfig.java` → `RulesEngine.java`:

| Rule | Default limit |
|---|---|
| Max retry attempts per item | 3 |
| Cooldown between retries | 60 minutes |
| Max discount the agent can offer | 15% |
| Minimum amount eligible for a discount | ₹500 |
| Actions requiring human sign-off | Discount above the ceiling, or 3rd consecutive failure |

These are enforced in plain Java (`RulesEngine`), evaluated **before** any LLM output is allowed to execute. The bounds are changeable at runtime via `PUT /api/config/bounds` — no server restart needed.

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

| Metric | Value |
|---|---|
| Transactions at risk | 320 |
| Recovered | 161 (50.3% recovery rate) |
| Revenue recovered | ₹1,25,86,357 |
| Intervention cost | ₹4,217 |
| **Net recovered** | **₹1,25,82,140** |
| Naive baseline (retry-once) | ₹47,81,706 |
| **Agent advantage** | **₹78,00,434 more than baseline** |

**By source:**
- Payment failures: 114/200 recovered (57%)
- Checkout abandonment: 29/80 recovered (36%)
- Overdue receivables: 18/40 recovered (45%)

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
| GET | `/api/metrics/dashboard` | Metrics + funnel + actions + efficiency in one response |
| GET | `/api/metrics/funnel` | Recovery pipeline status distribution |
| GET | `/api/metrics/actions` | Per-action success rate breakdown |
| GET | `/api/metrics/efficiency` | Per-action ROI (recovered per rupee spent) |
| GET | `/api/metrics/batches` | Per-batch metrics history |
| POST | `/api/recovery/run-batch` | Execute a recovery batch |
| GET | `/api/recovery/transactions` | All seeded transactions |
| GET | `/api/recovery/pending-review` | Items requiring human sign-off |
| GET | `/api/recovery/export` | CSV export of all attempts |
| GET | `/api/config/bounds` | Current recovery bounds |
| PUT | `/api/config/bounds` | Update bounds at runtime |

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
