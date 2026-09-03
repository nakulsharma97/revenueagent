# Demo Recording Script — 90 Seconds

Record this walkthrough in one continuous take. Use Loom (free), OBS, or your OS
built-in screen recorder. Target resolution: 1920×1080 or 1280×720.

Save the recording as `docs/demo.gif` and a still frame as `docs/demo-thumbnail.png`.

---

## Script

### 0:00 – 0:15  —  The Problem (voice-over, no interaction needed)

> "Every day, businesses lose revenue to payment failures, abandoned checkouts,
> and overdue invoices. Today, teams chase these manually. Our agent automates
> the full loop — detect, diagnose, decide, execute, and measure — while
> staying inside hard, auditable limits."

*(Screen: land on the Overview dashboard — stat cards and the agent-vs-baseline chart
are visible immediately.)*

---

### 0:15 – 0:35  —  The Bounds Register

Click **Bound Register** in the sidebar.

> "This is what makes it an agent, not just an LLM with API access.
> Max 3 retries. 60-minute cooldown. 15% discount ceiling. ₹500 minimum.
> Above these limits, the system requires human sign-off.
> These rules are enforced in plain Java before any LLM output runs."

*(Screen: hover over a couple of rows in the bounds table so the viewer reads them.)*

---

### 0:35 – 1:00  —  Run the Batch

Click **Overview** in the sidebar, then click the **Run Batch ▶** button.

> "320 at-risk items across payment failures, checkout abandonment, and B2B
> receivables. Let's watch it process."

*(Screen: ledger tape scrolls, stat cards update, charts repopulate.
Wait for the batch to finish — usually 2-4 seconds.)*

After it finishes, read the live numbers off the screen — recovery rate,
revenue recovered, and the Agent vs. Baseline chart:

> "Recovery rate [read the % off the stat card], net recovered
> [read off the card] versus the naive baseline [read off the chart] —
> that's the headline number."

> Note: the seeded dataset and mock outcomes are deterministic (fixed
> Random seed 42), but the exact figures are computed live from the actual
> batch, so always read them off the dashboard instead of scripting a
> specific number.

---

### 1:00 – 1:20  —  Decision Trace

Click into any row in the **Decision Ledger** table at the bottom of the
Overview page.

> "Every single decision is logged — what actions were eligible, which one
> the agent chose, why, with what confidence, and whether it required
> human sign-off."

*(Screen: the transaction modal/case-file shows the reasoning text and
the `llmDriven` / `requiresHumanSignoff` flags.)*

---

### 1:20 – 1:30  —  The Headline Chart

Scroll up to the **Net Recovered vs Baseline** chart.

> "This chart answers the brief: measured money recovered, compared to doing
> nothing smart. [Read the agent-vs-baseline delta off the chart] — on the
> same batch, same probability model. That's the number that proves this works."

*(Screen: end on this chart. Hold for 2-3 seconds, then stop recording.)*

---

## Recording Tips

- **No pauses** — keep it under 90 seconds with no dead air.
- **Cursor visible** — move the mouse deliberately so viewers follow your clicks.
- **No terminal** — judges don't need to see `mvn` or `npm`. Start with the
  frontend already loaded in the browser.
- **No API keys in frame** — if showing Settings, blur or crop the API key field.
- **One take** — if you stumble, just re-record. 90 seconds is short enough
  to nail on the second try.

## Thumbnail

Take a screenshot of the Overview page after running a batch (stat cards
populated, chart visible). Crop to 1280×720 and save as `docs/demo-thumbnail.png`.
