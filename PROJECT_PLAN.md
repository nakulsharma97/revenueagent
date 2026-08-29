# Project Plan — AI Revenue Recovery Agent
**Razorpay AI Buildathon · Track 03 · 5-Day Build Schedule**

---

## Section 1 — Page Architecture (single-page app, no router)

The app is one scrolling page with these sections in order:

1. **Statement Header / Letterhead** — Product name ("Revenue Recovery Agent"), batch metadata, "→ RUN BATCH" button
2. **Bounds Register** — Always visible above the fold. Static panel listing PROJECT_BRIEF.md section 3's hard limits verbatim. This is the single most important page for grading criteria — it proves the system is bounded.
3. **Ledger Tape** — Live scrolling feed of recovery outcomes (teleprinter style)
4. **Summary Statement** — Stat cards: at-risk count, recovered count, recovery rate, net recovered (styled as ledger line-items)
5. **Net Recovered vs Baseline** — Horizontal bar chart: agent performance vs naive retry-once baseline
6. **Funnel + Action Breakdown** — Two side-by-side panels: status distribution (AT_RISK→IN_RECOVERY→RECOVERED→LOST) and per-action success rates
7. **Pending Human Review** — Register of attempts requiring human sign-off, showing WHY (discount exceeded ceiling / 3rd consecutive failure)
8. **Decision Ledger** — Full transaction-by-transaction register with search/filter, click-through to case file
9. **Case File Modal** — Per-transaction detail: eligible actions, which one was picked, reasoning sentence, llmDriven flag, signoff flag
10. **Footer / Colophon** — "LLM proposes · Rules engine disposes" + link back to Bounds Register

---

## Section 2 — Design System: Aged Ledger Paper

### Color Palette
```
--bg: #F4F1E8          (aged paper background)
--ink: #1C1A14          (primary text — dark umber)
--ink-secondary: #5A5347 (secondary text)
--ink-muted: #8C8577     (muted/disabled text)
--ink-green: #2E7D32     (success/recovered)
--ink-red: #C62828       (failed/lost/alert)
--ink-amber: #F57F17     (warning/pending)
--ink-blue: #1565C0      (links/interactive)
--rule: 1px solid #C8C1B4 (hairline rules)
--rule-strong: 2px solid #1C1A14 (section dividers)
--surface: #FAF8F2       (card/panel background)
--surface-hover: #F0ECE0  (hover state)
```

### Typography
- **JetBrains Mono** — PRIMARY font for all numbers, IDs, amounts, tables, code references
- **IBM Plex Mono** — Display font for section headers, small-caps labels
- **Inter** — Body text only (long-form descriptions)

### Layout Rules
- Border radius: 0px everywhere. No rounded corners.
- No box-shadows. Structure comes from ruled hairlines only.
- Every panel uses `border: var(--rule)` or `border: var(--rule-strong)`
- Tabular-figure alignment in all tables
- Monospace numbers aligned to the right in financial columns

---

## Section 3 — Bounds Register Detail

Must look like a printed rulebook / compliance stamp block:
- Bordered box with `border: 2px solid var(--ink)`
- Small-caps title row: "SECTION 3 — NON-NEGOTIABLE CONSTRAINTS"
- Two-column table: RULE | LIMIT | ENFORCED BY
- Rules listed verbatim from PROJECT_BRIEF.md section 3
- Below the rules: "ALLOWED ACTIONS" — the 6 actions the LLM may only pick from
- Footer: "LLM proposes · Rules engine disposes"

---

## Section 4 — Information Architecture (exact section order)

| # | Section | Visual treatment | Data source |
|---|---------|-----------------|-------------|
| 1 | Bounds Register | Rulebook block, always visible | Static content |
| 2 | Summary Statement | 4 stat cards as ledger line-items | GET /api/metrics |
| 3 | Net Recovered vs Baseline | Horizontal bar chart | GET /api/metrics |
| 4 | Funnel + Action Breakdown | Two side-by-side panels | GET /api/metrics/funnel, /api/metrics/actions |
| 5 | Pending Human Review | Red-bordered register | GET /api/recovery/pending-review |
| 6 | Decision Ledger | Full table with search/filter | POST /api/recovery/run-batch response |
| 7 | Case File Modal | Modal overlay | Attempt object from table row |

---

## Section 5 — Live Bounds Editor (Day 2 differentiator)

The Bounds Register must include an inline editor allowing runtime changes to:
- Max retries (default: 3)
- Max discount percent (default: 15%)
- Min amount for discount (default: ₹500)

Changes take effect on the NEXT batch run — must go through real RulesEngine, never bypass enforceBounds().

API:
- `GET /api/config/bounds` — returns current values
- `PUT /api/config/bounds` — accepts JSON body with new values

---

## Section 6 — Demo Script (3-minute walkthrough)

1. **Open dashboard** — Show bounds register above the fold (judge sees constraints FIRST)
2. **Live-edit bounds** — Change max discount from 15% to 8% using the inline editor
3. **Click "→ RUN BATCH"** — Watch ledger tape populate, stat cards update
4. **Point to net-recovered-vs-baseline chart** — Headline number: agent outperforms naive baseline
5. **Scroll to Pending Human Review** — Show flagged attempts (3rd failures / discount caps)
6. **Click a decision ledger row** — Open case file modal, show reasoning sentence + llmDriven flag
7. **End** — "LLM proposes · Rules engine disposes"

---

## Section 7 — API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | /api/recovery/run-batch | Execute full recovery loop |
| GET | /api/recovery/transactions | List all transactions |
| GET | /api/recovery/pending-review | Attempts requiring human signoff |
| GET | /api/recovery/export | CSV export of all attempts |
| GET | /api/metrics | Current batch metrics |
| GET | /api/metrics/funnel | Status distribution |
| GET | /api/metrics/actions | Per-action breakdown |
| GET | /api/metrics/batches | Batch history |
| GET | /api/config/bounds | Current rules engine config |
| PUT | /api/config/bounds | Update rules engine config |

---

## Section 8 — Tech Stack

| Layer | Choice |
|-------|--------|
| Backend | Spring Boot 3 / Java 21 |
| DB | H2 in-memory (dev), MySQL (prod) |
| Decision | RulesEngine + Claude API (optional) |
| Frontend | React + Vite, Recharts |
| Mock services | MockPaymentGatewayService, MockNotificationService |
