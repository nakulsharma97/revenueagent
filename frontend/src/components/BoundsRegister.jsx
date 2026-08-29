/**
 * Bounds Register — the single most important panel in the entire dashboard.
 * Lists the hard limits from PROJECT_BRIEF.md section 3 verbatim, rendered as
 * a literal ruled register/rulebook. This is what a judge should see FIRST,
 * before any numbers, because it's the thing that makes this "an agent" and
 * not "an LLM with API access."
 *
 * "The LLM proposes; the rules engine disposes." — this is the proof.
 */

const RULES = [
  { id: 'R1', rule: 'Max retry attempts per transaction', limit: '3', code: 'RulesEngine.eligibleActions(tx)' },
  { id: 'R2', rule: 'Cooldown between retries', limit: '60 minutes', code: 'RecoveryOrchestratorService.processOne()' },
  { id: 'R3', rule: 'Max discount the agent can offer', limit: '15%', code: 'RulesEngine.enforceBounds()' },
  { id: 'R4', rule: 'Min transaction amount eligible for discount', limit: '₹500', code: 'RulesEngine.eligibleActions()' },
  { id: 'R5', rule: 'Human sign-off: discount above ceiling', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
  { id: 'R6', rule: 'Human sign-off: 3rd consecutive failure', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
];

const ACTIONS_ALLOWLIST = [
  { action: 'RETRY_NOW', description: 'Immediate payment retry' },
  { action: 'RETRY_SCHEDULED', description: 'Retry after cooldown period' },
  { action: 'SEND_PAYMENT_LINK', description: 'Nudge customer to update payment method' },
  { action: 'OFFER_DISCOUNT', description: 'Incentivize fresh payment (max 15%)' },
  { action: 'ESCALATE_TO_HUMAN', description: 'Route to human collections team' },
  { action: 'ABANDON', description: 'Write off as unrecoverable' },
];

export default function BoundsRegister() {
  return (
    <div className="panel" style={{ border: '2px solid var(--text)' }}>
      {/* Header — compliance stamp block */}
      <div style={{
        padding: '14px 20px',
        borderBottom: '2px solid var(--text)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 9,
            fontWeight: 600,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: 'var(--text-muted)',
            marginBottom: 3,
          }}>
            SECTION 3 — NON-NEGOTIABLE CONSTRAINTS
          </div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 15,
            fontWeight: 700,
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
            color: 'var(--text)',
          }}>
            BOUNDS REGISTER
          </div>
        </div>
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: 10,
          color: 'var(--text-muted)',
          textAlign: 'right',
          lineHeight: 1.6,
        }}>
          <div>ENFORCED IN PLAIN JAVA</div>
          <div style={{ fontWeight: 600 }}>BEFORE ANY LLM OUTPUT EXECUTES</div>
        </div>
      </div>

      {/* Rules table */}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12, fontFamily: 'var(--font-mono)' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid var(--text)', textAlign: 'left' }}>
            {['REF', 'RULE', 'LIMIT', 'ENFORCED BY'].map(h => (
              <th key={h} style={{
                padding: '8px 12px',
                fontFamily: 'var(--font-display)',
                fontSize: 9,
                fontWeight: 600,
                letterSpacing: '0.1em',
                color: 'var(--text-muted)',
                textTransform: 'uppercase',
              }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {RULES.map((r, idx) => (
            <tr key={r.id} style={{ borderBottom: 'var(--rule)' }}>
              <td style={{
                padding: '7px 12px',
                fontWeight: 700,
                color: r.limit === 'REQUIRED' ? 'var(--ink-red)' : 'var(--text)',
                width: 50,
              }}>
                {r.id}
              </td>
              <td style={{ padding: '7px 12px', color: 'var(--text)' }}>
                {r.rule}
              </td>
              <td style={{
                padding: '7px 12px',
                fontWeight: 700,
                color: r.limit === 'REQUIRED' ? 'var(--ink-red)' : 'var(--ink-blue)',
                width: 120,
              }}>
                {r.limit}
              </td>
              <td style={{
                padding: '7px 12px',
                color: 'var(--text-muted)',
                fontSize: 11,
              }}>
                {r.code}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Allow-list section */}
      <div style={{ borderTop: '2px solid var(--text)', padding: '12px 20px' }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 9,
          fontWeight: 600,
          letterSpacing: '0.1em',
          textTransform: 'uppercase',
          color: 'var(--text-muted)',
          marginBottom: 8,
        }}>
          ALLOWED ACTIONS — THE LLM MAY ONLY PICK FROM THIS LIST
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 0 }}>
          {ACTIONS_ALLOWLIST.map((a, idx) => (
            <div key={a.action} style={{
              flex: '1 1 180px',
              padding: '6px 10px',
              borderRight: idx < ACTIONS_ALLOWLIST.length - 1 ? 'var(--rule)' : 'none',
              borderBottom: 'var(--rule)',
            }}>
              <div style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 11,
                fontWeight: 600,
                color: 'var(--text)',
              }}>
                {a.action}
              </div>
              <div style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 10,
                color: 'var(--text-muted)',
                marginTop: 2,
              }}>
                {a.description}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Footer */}
      <div style={{
        borderTop: 'var(--rule)',
        padding: '8px 20px',
        background: 'var(--surface-2)',
        fontFamily: 'var(--font-mono)',
        fontSize: 10,
        color: 'var(--text-muted)',
        display: 'flex',
        justifyContent: 'space-between',
      }}>
        <span>LLM proposes · Rules engine disposes</span>
        <span style={{ fontWeight: 600 }}>RulesEngine.enforceBounds() — EVERY ACTION VALIDATED</span>
      </div>
    </div>
  );
}
