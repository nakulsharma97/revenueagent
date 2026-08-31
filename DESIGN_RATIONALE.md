# Design Rationale — Why We Built It This Way

> This section is written in first person because its value is being genuinely original — it reflects the actual trade-offs we made, not generic documentation.

---

## Why three revenue sources instead of one deep vertical

The track brief listed six directions: checkout drop-off, failed-subscription recovery, B2B receivables, mandate retry, Hinglish voice recovery, and promise-to-pay tracking. My first instinct was to pick one and go deep — the project brief in this repo even says "one deep vertical" was the original plan.

But during the design phase I kept running into the same problem: a single revenue source (say, subscription dunning) makes the "bounded workflow" proof too easy. If you only have payment retries, the RulesEngine is just an if-else on retryCount. There's no real tension between "what the AI wants to do" and "what the rules allow."

Three sources create that tension. A UPI PIN mismatch wants a retry; a broken promise-to-pay wants a follow-up call; a cart abandonment wants a discount nudge — and each source has different eligible actions, different cost structures, and different success probabilities. The RulesEngine becomes genuinely interesting when it has to reason across different entity types with different constraints.

I also wanted the dashboard to prove something real: that the same bounded agent generalises across revenue types, not just one narrow case. The held-out evaluation split (20% of each source, never seen by the agent) is specifically there to make that claim credible.

**What I'd do differently with more time:** I'd add a fourth source — UPI mandate failures (e-mandate cancellations for SaaS billing). This is a massive pain point in India that none of the other buildathon submissions model, and it would have let me demonstrate the agent handling automated recurring payment failures vs one-time failures.

---

## Why Java/Spring Boot over Python or Node

Honestly? Because it's what I know best, and a buildathon is not the time to learn a new stack under pressure.

But beyond comfort, there's a real architectural reason: the bounded workflow enforcement needs to be airtight. In Python, decorators and metaclasses can intercept method calls, but there's no compile-time guarantee that a new action type gets handled. In Java, the `switch` statements in `RulesEngine.eligibleActions()` and `MockPaymentGatewayService.attemptCharge()` are exhaustive — if someone adds a new `RecoveryAction` enum value, the compiler forces them to handle it in every switch. That's the kind of safety net you want when the entire project's thesis is "the AI never acts outside hard limits."

Spring Boot's `@Transactional` and `@PostConstruct` also made the data seeding and idempotency logic straightforward. The H2 in-memory database means zero setup for judges — they clone the repo, run `mvn spring-boot:run`, and the dashboard is populated with 320 realistic items in 12 seconds.

**What I'd do differently with more time:** I'd evaluate whether the backend could be replaced with a lighter framework like Quarkus or Micronaut for faster startup time. The 12-second cold start isn't terrible, but for a demo it matters.

---

## One thing I'd do differently with more time

The mock gateway and notification services are too simple. Right now `MockPaymentGatewayService.attemptCharge()` returns `random.nextDouble() < successProbability` — it's a coin flip. In production, you'd want the mock to model actual gateway behaviour: session timeouts, partial successes, idempotency failures, rate limiting.

I didn't do this because the buildathon scope was already large (three revenue sources, rules engine, LLM integration, dashboard, metrics, held-out evaluation), and the mock's job is to generate realistic-enough outcomes for the metrics to be meaningful. The probabilities are calibrated against NPCI/RBI published data, so the recovery rates are realistic even if the mechanism is simplified.

But if I had another week, I'd replace the mocks with a stateful simulation that tracks session state across retries — that would make the idempotency guard and cooldown logic actually exercise real edge cases instead of just being code that runs without failing.

---

## Why the LLM sits inside the rules boundary

This is the single most important design decision in the project.

The track brief says "bounded workflow" and "hard human-set bounds." I interpreted this literally: the AI must be architecturally incapable of exceeding the limits, not just unlikely to. The flow is:

1. `RulesEngine.eligibleActions(tx)` returns an allow-list of 2-4 actions
2. The LLM (or heuristic fallback) picks one action from that list and justifies the pick
3. `RulesEngine.enforceBounds(tx, proposed)` re-validates the choice — if the LLM somehow returned an action outside the allow-list, it gets silently corrected to the safest fallback

The LLM never sees a world where it can propose a 40% discount or a 4th retry. It doesn't know those options exist. This is fundamentally different from "the LLM proposes, a human reviews" — here, the rules engine is the enforcement layer, and human sign-off is only required for edge cases (discount above ceiling, 3rd consecutive failure).

---

## Why the held-out evaluation split exists

Most buildathon demos show recovery rates on the same data the agent processed. That's like measuring exam performance on the practice questions — it proves nothing about generalisation.

The 20% held-out split (random, fixed seed, never read by the agent or the rules engine) lets me say "the recovery rate on unseen data is X%" — which is a credible claim about real-world performance, not just batch performance. This is the kind of thing a judge would notice and appreciate, because it shows I'm thinking about honest evaluation, not just impressive-looking numbers.
