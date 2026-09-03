# PROJECT BRIEF — RecoveryOS

**RecoveryOS — An Autonomous Revenue Recovery Intelligence System**

Razorpay Build submission (Track 03). Backend: Spring Boot 3 / Java 21 / H2 (or MySQL). Frontend: React + Vite. No external paid services required to run the full demo (an Anthropic key is optional and used only for the *explanation* layer).

## The idea

Most payment-recovery products answer one question: *"which failed payment should I retry?"*

RecoveryOS answers a different, higher-value question:

> **For this specific customer, at this specific time, what is the NEXT BEST RECOVERY ACTION that creates the highest incremental net revenue?**

Instead of one retry policy, the system treats every case as a portfolio of possible actions — **do nothing, retry now, retry later, silent retry, reminder, payment link, 5–25% discount, payment plan, escalation, stop** — counterfactually simulates each one, prices its expected net value, and executes only the economically best action that also satisfies hard policy bounds. Then it records the outcome and learns.

## The pipeline

```
Transaction/abandonment/invoice
        ↓
Feature builder (amount, cause, retries, reliability, segment)
        ↓
Customer state analysis (NEW_FAILURE → … → STOP_INTERVENTION)
        ↓
Recovery-fatigue score (0 fresh → 1 severe)
        ↓
Counterfactual simulation of EVERY eligible action
        ↓
Net value = expected recovery − discount cost − intervention cost − risk penalty
        ↓
Policy constraints (RulesEngine bounds + uplift segment + fatigue policy)
        ↓
NEXT BEST ACTION (highest valid incremental net value)
        ↓
Confidence band → automation policy (≥85% auto · 60–85% safe · <60% human)
        ↓
Bounded execution
        ↓
Outcome recorded → Action Performance Lab / outcome learning
```

Key property: **the action is always chosen by the deterministic structured engine.** An optional LLM may only explain the choice in natural language afterwards (`llmDriven=true` only when a real API response was incorporated; otherwise it stays false — nothing is faked).

## Why it is not a generic retry dashboard

1. **Counterfactual decisions are first-class data.** Every batch run persists one row per simulated action (`counterfactual_decisions`), flagging the selected row — the UI renders the full “what else was considered” bar chart per decision.
2. **Incremental value, not headline success.** The engine optimises recovery *lift* over a natural baseline and subtracts the true cost of margin (discounts) and risk. It will refuse a “90% success” discount that gives away more margin than it creates.
3. **Cause-aware economics.** Discounts are barely priced for transient network/UPI failures (they do not fix a timeout) but are meaningful for price-hesitant carts and method-blocked cardholders; pay-links dominate when the customer must act themselves.
4. **Fatigue is a first-class constraint.** After repeated touchpoints the engine de-escalates: drops discounts → drops contact → hands to a human. No spam.
5. **Humans are in the loop by policy, not by accident.** Confidence < 60%, last retry before the segment limit, discount-cap events and HIGH/CRITICAL anomalies all route to the Human Review Queue, where a reviewer can approve, override or reject — audited.
6. **Outcome learning is wired.** Every execution produces a `recovery_outcome` row; the Action Performance Lab ranks actions by *net* value and exposes the data shape a future ML model would train on.
7. **Experimentation is declared, not mixed in.** Control/treatment assignment lives in ingestion (`isControlGroup` + uplift segmentation); `recovery_experiments` documents what is being tested, on which segment, and at what control percentage — experiment logic never touches per-entity decisions.

## Domain vocabulary

| Term | Meaning |
|---|---|
| Next-Best-Action | the single action with the highest valid incremental net value |
| Incremental net value | expected net recovery minus what the customer would pay anyway |
| Counterfactual decision | persisted row of “what if action X had run” |
| Customer recovery state | NEW_FAILURE · SOFT_RISK · REPEATED_FAILURE · HIGH_VALUE_AT_RISK · RECOVERY_FATIGUE · LIKELY_TO_SELF_RECOVER · DISCOUNT_SENSITIVE · HUMAN_ATTENTION_REQUIRED · STOP_INTERVENTION |
| Fatigue | 0–1 score → LOW / MODERATE / HIGH / SEVERE automation bands |
| Confidence policy | ≥0.85 AUTO_EXECUTE · 0.60–0.85 SAFE_ACTION_ONLY · <0.60 HUMAN_REVIEW |
| Review case | approve / override / reject a recommendation (audited) |
| Outcome record | the training row after an action executes |

## Modules (backend `com.razorpay.recovery.intelligence`)

`NextBestActionEngine`, `UpliftScoringService`, `RecoveryFatigueService`, `CustomerStateService`, `DecisionConfidenceService`, `RecoveryValueOptimizer`, `AnomalyDetectionService`, `OutcomeLearningService`, `RecoveryIntelligenceService` (transactional coordinator), plus persisted entities `CounterfactualDecision`, `RecoveryOutcome`, `HumanReviewCase`, `RecoveryAnomaly`, `RecoveryExperiment`. The existing bounded core — `RulesEngine`, `RecoveryOrchestratorService` (single processing core for REST + SSE + startup + scheduler), DTO API layer, idempotency, audit — is preserved and used unchanged where appropriate.

Every intelligence class is a Spring-managed bean. `NextBestActionEngine` is a single injected singleton whose collaborators (scoring, fatigue, state, confidence, value optimizer, anomalies) are constructor-injected — no production code constructs the engine by hand, so REST, SSE, scheduler, startup and simulator runs all share the identical decision rules. Each attempt records its provenance (`decisionSource = RECOVERY_INTELLIGENCE_ENGINE | FALLBACK_HEURISTIC | MANUAL_HUMAN_OVERRIDE`, `engineVersion = RECOVERY_INTELLIGENCE_V1`) so the demo can prove the intelligence engine — not a heuristic or a fake LLM call — produced every decision. There is no LLM-picker left in the codebase: the legacy "ask the model to choose an action" path was deleted, and the only LLM surface is the optional explanation layer (`llmDriven=true` strictly requires a real API response). Every executed action is also remembered by the **Outcome Memory** (`OutcomeMemoryService`, `GET /api/intelligence/outcome-memory`) as a (source × failure context × customer segment × action) prior, so the demo can show what has historically worked for a specific situation rather than a flat per-action average.

## New tables

Created automatically by `ddl-auto` (H2 `create-drop` / MySQL `update`): `counterfactual_decisions`, `recovery_outcomes`, `human_review_cases`, `recovery_anomalies`, `recovery_experiments`; columns `discountPercent`, `recoveryState`, `fatigueScore` added to `recovery_attempts`.

## Determinism

The engine and all scenario expectations are deterministic (no randomness in decisions). Demo data is seeded with a fixed random seed for outcome draws, so recovery *numbers* vary slightly per run — docs never quote fixed revenue totals; they quote fixed *decisions*. See “Predictable demo scenarios” in the README for the ten named cases (self-heal → silent retry, dead card → pay-link, high-value → wider discount ceiling, fatigued → stop, big-ticket → anomaly + review, etc.).

## Screens

Command Center · Recovery Simulator · Human Review · Action Lab (performance + experiments) · Bound Register · Transactions · Actions · Decision Ledger (with counterfactual + timeline detail) · Reports · Alerts · Settings.
