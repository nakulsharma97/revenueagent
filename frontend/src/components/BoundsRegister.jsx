const RULES = [
  { id: 'R1', rule: 'Max retry attempts per transaction', limit: '3', code: 'RulesEngine.eligibleActions(tx)' },
  { id: 'R2', rule: 'Cooldown between retries', limit: '60 minutes', code: 'RecoveryOrchestratorService.processOne()' },
  { id: 'R3', rule: 'Max discount the agent can offer', limit: '15%', code: 'RulesEngine.enforceBounds()' },
  { id: 'R4', rule: 'Min transaction amount eligible for discount', limit: '₹500', code: 'RulesEngine.eligibleActions()' },
  { id: 'R5', rule: 'Human sign-off: discount above ceiling', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
  { id: 'R6', rule: 'Human sign-off: 3rd consecutive failure', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
];

export default function BoundsRegister() {
  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
        <div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 15,
            fontWeight: 700,
            color: 'var(--text)',
            marginBottom: 2,
          }}>
            BOUNDS REGISTER
          </div>
          <div style={{
            fontFamily: 'var(--font-body)',
            fontSize: 12,
            color: 'var(--text-muted)',
          }}>
            Section 3 — Non-Negotiable Constraints
          </div>
        </div>
        <div style={{
          fontFamily: 'var(--font-body)',
          fontSize: 11,
          color: 'var(--text-muted)',
          textAlign: 'right',
        }}>
          <div>Enforced in plain Java</div>
          <div style={{ fontWeight: 600 }}>before any LLM output executes</div>
        </div>
      </div>

      {/* Rules table */}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
        <thead>
          <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
            {['Ref', 'Rule', 'Limit', 'Enforced By'].map(h => (
              <th key={h} style={{
                padding: '8px 10px',
                fontFamily: 'var(--font-body)',
                fontSize: 11,
                fontWeight: 600,
                color: 'var(--text-muted)',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {RULES.map((r) => (
            <tr key={r.id} style={{ borderBottom: '1px solid var(--border)' }}>
              <td style={{
                padding: '8px 10px',
                fontWeight: 700,
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: r.limit === 'REQUIRED' ? 'var(--ink-red)' : 'var(--text)',
                width: 50,
              }}>
                {r.id}
              </td>
              <td style={{ padding: '8px 10px', color: 'var(--text)', fontFamily: 'var(--font-body)' }}>
                {r.rule}
              </td>
              <td style={{
                padding: '8px 10px',
                fontWeight: 700,
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: r.limit === 'REQUIRED' ? 'var(--ink-red)' : 'var(--ink-blue)',
                width: 120,
              }}>
                {r.limit}
              </td>
              <td style={{
                padding: '8px 10px',
                color: 'var(--text-muted)',
                fontFamily: 'var(--font-mono)',
                fontSize: 11,
              }}>
                {r.code}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
