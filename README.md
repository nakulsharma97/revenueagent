# Revenue Recovery Agent

**An AI agent that detects revenue at risk, determines the right intervention inside hard human-set bounds, executes it, and reports honest recovered-vs-cost metrics.**

> Razorpay AI Buildathon · Track 03 — AI Revenue Recovery

<!-- TODO: Record a 90-second screen capture, save as docs/demo.gif + docs/demo-thumbnail.png,
     then uncomment the line below. See docs/DEMO_SCRIPT.md for the walkthrough outline. -->
<!-- [![Demo](docs/demo-thumbnail.png)](docs/demo.gif) -->

## What this does

The agent runs a **detect → diagnose → decide → execute → measure** loop across three revenue sources — payment failures, checkout abandonment, and overdue receivables. A rules engine enforces hard limits (max retries, cooldown, discount ceiling) **before** any LLM output is allowed to execute. The LLM proposes; the rules engine disposes. Every decision is logged with full reasoning, and a net-recovered-vs-baseline chart proves measured value on the same batch.

## Architecture

```
backend/   Spring Boot 3 (Java 21) — detection, rules engine, LLM decision layer, mock execution, metrics
frontend/  React + Vite — live dashboard, 8-page SPA with dark premium theme
```

**Flow:** `Detection → Diagnosis → Decision (RulesEngine bounds + LLM reasoning) → Execution (mocked) → Metrics`

### Key files

| What it proves | File |
|---|---|
| **Bounded workflow** (the hard limits) | `backend/.../recovery/RulesEngine.java` |
| Decision layer + LLM prompt | `backend/.../recovery/DecisionAgentService.java` |
| End-to-end loop (all 3 sources) | `backend/.../recovery/RecoveryOrchestratorService.java` |
| Honest metrics (recovered − cost) | `backend/.../metrics/MetricsService.java` |
| Realistic synthetic batch (320 items) | `backend/.../config/DataSeeder.java` |
| Human sign-off queue | `backend/.../recovery/RecoveryController.java` (`GET /pending-review`) |
| Live bounds editor | `backend/.../config/ConfigController.java` (`PUT /config/bounds`) |

### Non-negotiable constraints (enforced by RulesEngine)

| Rule | Limit |
|---|---|
| Max retry attempts per transaction | 3 |
| Cooldown between retries | 60 minutes |
| Max discount the agent can offer | 15% |
| Minimum amount eligible for discount | ₹500 |
| Actions requiring human sign-off | Discount above ceiling or 3rd consecutive failure |

## Setup (verified — copy-paste these commands)

### Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` with an in-memory H2 database — **no setup required**. 320 synthetic at-risk items (200 payment failures + 80 checkout abandonments + 40 overdue receivables) are seeded on startup.

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

Without these, the agent falls back to an explainable heuristic path — the demo never breaks.

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

1. Start the backend — 320 at-risk items are already seeded.
2. Start the frontend, open it, click **Run Batch**.
3. Watch the stat cards, charts, and decision ledger populate.
4. The **Agent vs. Baseline** chart is the headline number.
5. Click any row in the decision ledger to see the agent's reasoning.
6. Check **Alerts** for items requiring human sign-off.
7. Use **Settings** to change recovery bounds at runtime and re-run.

## API endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/metrics` | Combined metrics across all 3 sources |
| GET | `/api/metrics/funnel` | Recovery pipeline status distribution |
| GET | `/api/metrics/actions` | Per-action success rate breakdown |
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
