# Revenue Recovery Agent
**Razorpay AI Buildathon · Track 03 — AI Revenue Recovery**

An agent that detects at-risk subscription payments, decides the right recovery action inside hard, human-set bounds, executes it, and reports honest recovered-vs-cost metrics on the batch.

**Measured result (300-transaction batch, heuristic fallback):** 50.3% recovery rate — ₹4,18,649 recovered minus ₹7,574 intervention cost = **₹4,11,075 net recovered**, compared to a ₹3,02,365 naive retry-once baseline. The agent recovers ₹1,08,710 more than the baseline on the same batch.

See `PROJECT_BRIEF.md` for the full problem breakdown, scope decision, and design rationale.

## Architecture

```
backend/   Spring Boot 3 (Java 21) — detection, rules engine, LLM decision layer, mock execution, metrics
frontend/  React + Vite — live dashboard: stat cards, agent-vs-baseline chart, decision ledger
```

Flow: `Detection → Diagnosis → Decision (RulesEngine bounds + LLM reasoning) → Execution (mocked) → Metrics`

The core design decision: **the LLM proposes, the rules engine disposes.** `RulesEngine.eligibleActions()` computes the allow-list for a transaction; the LLM (or the deterministic fallback, if no API key is set) can only choose from that list; `RulesEngine.enforceBounds()` re-validates the choice before anything executes. Nothing the model says can push a retry past the configured limit or a discount past the configured ceiling.

## Running it

### Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` using an in-memory H2 database — no setup required. 300 synthetic at-risk transactions are seeded on startup (fixed random seed, so the numbers are reproducible run to run).

To use real MySQL instead: `mvn spring-boot:run -Dspring-boot.run.profiles=mysql` (set `DB_USER` / `DB_PASSWORD` env vars if needed).

To enable live LLM reasoning instead of the deterministic fallback:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export LLM_ENABLED=true
mvn spring-boot:run
```
Without these two variables set, the app still runs end-to-end — `DecisionAgentService` falls back to an explainable heuristic path, so a demo never breaks on a missing key or a flaky network call.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`. Point it at a different backend with `VITE_API_BASE` (see `.env.example`).

## Using it

1. Start the backend — 300 at-risk transactions are already seeded.
2. Start the frontend, open it, click **Run recovery batch**.
3. Watch the ledger tape and stat cards populate; click any row in the decision table to see the agent's one-line reasoning for that transaction.
4. The bar chart is the headline number: net revenue recovered by the agent vs. a naive "retry once" baseline, same batch.

## Key files to walk a judge through

| What it proves | File |
|---|---|
| Bounded workflow (the hard limits) | `backend/.../service/RulesEngine.java` |
| Decision layer + LLM prompt | `backend/.../service/DecisionAgentService.java` |
| End-to-end loop | `backend/.../service/RecoveryOrchestratorService.java` |
| Honest metrics (recovered − cost) | `backend/.../service/MetricsService.java` |
| Realistic synthetic batch | `backend/.../config/DataSeeder.java` |
