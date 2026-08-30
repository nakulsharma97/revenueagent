# Revenue Recovery Agent

**Razorpay AI Buildathon · Track 03 — AI Revenue Recovery**

An AI agent that detects revenue at risk across **payment failures, checkout abandonment, and overdue receivables**, determines the right intervention inside hard human-set bounds, executes it, and reports honest recovered-vs-cost metrics.

**Measured result (320-item multi-source batch, heuristic fallback):** 49.7% recovery rate — ₹1,20,48,276 recovered minus ₹6,118 intervention cost = **₹1,20,42,158 net recovered**, compared to a ₹48,81,706 naive retry-once baseline. The agent recovers **₹71,60,452 more** than the baseline on the same batch.

See `PROJECT_BRIEF.md` and `PROJECT_PLAN.md` for the full problem breakdown, scope decision, and design rationale.

## Architecture

```
backend/   Spring Boot 3 (Java 21) — detection, rules engine, LLM decision layer, mock execution, metrics
frontend/  React + Vite — live dashboard with dark premium theme, 8-page SPA
```

**Flow:** `Detection → Diagnosis → Decision (RulesEngine bounds + LLM reasoning) → Execution (mocked) → Metrics`

**Three revenue sources covered:**
- **Payment failures** (subscription dunning) — 200 seeded transactions
- **Checkout abandonment** (cart recovery) — 80 seeded sessions
- **Overdue receivables** (B2B invoice recovery) — 40 seeded invoices

The core design decision: **the LLM proposes, the rules engine disposes.** `RulesEngine.eligibleActions()` computes the allow-list for a transaction; the LLM (or the deterministic fallback, if no API key is set) can only choose from that list; `RulesEngine.enforceBounds()` re-validates the choice before anything executes. Nothing the model says can push a retry past the configured limit or a discount past the configured ceiling.

## Key features

| Feature | What it proves |
|---|---|
| **Bounded workflow** | RulesEngine enforces max retries (3), cooldown (60min), max discount (15%), min amount (₹500) |
| **Human sign-off queue** | Attempts requiring human review (discount above ceiling or 3rd failure) are flagged and surfaced |
| **Live bounds editor** | Change recovery limits at runtime via Settings — no restart needed |
| **Agent vs. Baseline chart** | Real simulated baseline on the same batch — the headline "measured" metric |
| **Batch history** | Per-batch metrics so repeated runs are distinguishable |
| **Concurrency lock** | Prevents overlapping batch runs |
| **LLM + heuristic fallback** | Works with or without an API key — demo never breaks |
| **CSV export** | Full decision ledger exportable for audit |

## Running it

### Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` using an in-memory H2 database — no setup required. 320 synthetic at-risk items (200 payment + 80 checkout + 40 receivable) are seeded on startup.

To use real MySQL instead:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# Set DB_USER / DB_PASSWORD env vars if needed
```

To enable live LLM reasoning:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export LLM_ENABLED=true
mvn spring-boot:run
```

Without these variables set, the app still runs end-to-end — `DecisionAgentService` falls back to an explainable heuristic path.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`. Set `VITE_API_BASE` to point at a different backend (see `.env.example`).

## Using it

1. Start the backend — 320 at-risk items are already seeded.
2. Start the frontend, open it, click **Run Batch**.
3. Watch the stat cards, charts, and decision ledger populate.
4. The agent-vs-baseline chart is the headline number: net revenue recovered by the agent vs. a naive "retry once" baseline.
5. Click any row in the decision ledger to see the agent's reasoning.
6. Check the **Alerts** page for items requiring human sign-off.
7. Use **Settings** to change recovery bounds at runtime and re-run.

## API endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/metrics` | Combined metrics across all 3 sources |
| GET | `/api/metrics/funnel` | Recovery pipeline status distribution |
| GET | `/api/metrics/actions` | Per-action success rate breakdown |
| GET | `/api/metrics/batches` | Per-batch metrics history |
| POST | `/api/recovery/run-batch` | Execute a recovery batch across all sources |
| GET | `/api/recovery/transactions` | All seeded transactions |
| GET | `/api/recovery/pending-review` | Items requiring human sign-off |
| GET | `/api/recovery/export` | CSV export of all attempts |
| GET | `/api/config/bounds` | Current recovery bounds |
| PUT | `/api/config/bounds` | Update bounds at runtime |

## Project structure (backend)

```
com.razorpay.recovery/
├── transaction/          Transaction + TransactionRepository
├── checkout/             CheckoutSession + CheckoutSessionRepository
├── receivable/           Receivable + ReceivableRepository
├── customer/             Customer + CustomerRepository
├── subscription/         Subscription + SubscriptionRepository
├── recovery/             RecoveryAttempt + Orchestrator + DecisionAgent + RulesEngine + Controller + DTOs
│   └── mocks/            MockPaymentGateway + MockNotification
├── metrics/              MetricsService + Controller + DTOs
├── config/               BoundsConfig + DataSeeder + ConfigController
├── scheduler/            RecoveryScheduler
└── RevenueRecoveryAgentApplication.java
```

## Key files to walk a judge through

| What it proves | File |
|---|---|
| Bounded workflow (the hard limits) | `backend/.../recovery/RulesEngine.java` |
| Decision layer + LLM prompt | `backend/.../recovery/DecisionAgentService.java` |
| End-to-end loop (all 3 sources) | `backend/.../recovery/RecoveryOrchestratorService.java` |
| Honest metrics (recovered − cost) | `backend/.../metrics/MetricsService.java` |
| Realistic synthetic batch | `backend/.../config/DataSeeder.java` |
| Human sign-off queue | `backend/.../recovery/RecoveryController.java` (GET `/pending-review`) |
| Live bounds editor | `backend/.../config/ConfigController.java` (PUT `/config/bounds`) |

## Demo script (3-minute walkthrough)

1. **Show the Bounds Register** — the hard limits that make this "an agent, not an LLM with API access"
2. **Click into one transaction** — show the rules engine narrowing to eligible actions, then the agent's reasoning
3. **Run the batch** — watch the charts populate with real numbers
4. **Show Agent vs. Baseline** — the headline chart proving measured value
5. **Check Alerts** — show items requiring human sign-off (bounded workflow proof)
6. **Change a bound live** — lower the discount ceiling in Settings, re-run, show more sign-offs

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3 / Java 21 |
| Database | H2 in-memory (dev) / MySQL (prod) |
| Decision | RulesEngine (plain Java) + Claude API (optional) |
| Frontend | React + Vite, Recharts |
| Mock services | MockPaymentGatewayService, MockNotificationService |
