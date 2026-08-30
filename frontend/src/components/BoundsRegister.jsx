const RULES = [
  { id: 'R1', rule: 'Max retry attempts per transaction', limit: '3', code: 'RulesEngine.eligibleActions(tx)' },
  { id: 'R2', rule: 'Cooldown between retries', limit: '60 minutes', code: 'RecoveryOrchestratorService.processOne()' },
  { id: 'R3', rule: 'Max discount the agent can offer', limit: '15%', code: 'RulesEngine.enforceBounds()' },
  { id: 'R4', rule: 'Min transaction amount eligible for discount', limit: '₹500', code: 'RulesEngine.eligibleActions()' },
  { id: 'R5', rule: 'Human sign-off: discount above ceiling', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
  { id: 'R6', rule: 'Human sign-off: 3rd consecutive failure', limit: 'REQUIRED', code: 'RulesEngine.requiresHumanSignoff()' },
];

export default function BoundsRegister({ expanded }) {
  return (
    <div className="card" style={{ width: '100%', minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
        <div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>BOUNDS REGISTER</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Section 3 — Non-Negotiable Constraints</div>
        </div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', textAlign: 'right', flexShrink: 0 }}>
          <div>Enforced in plain Java</div>
          <div style={{ fontWeight: 600, color: 'var(--text-secondary)' }}>before any LLM output executes</div>
        </div>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left' }}>
              <th style={{ padding: '8px 12px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', width: 50 }}>Ref</th>
              <th style={{ padding: '8px 12px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Rule</th>
              <th style={{ padding: '8px 12px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', width: 130 }}>Limit</th>
              <th style={{ padding: '8px 12px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Enforced By</th>
            </tr>
          </thead>
          <tbody>
            {RULES.map(r => (
              <tr key={r.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                <td style={{ padding: '8px 12px', fontWeight: 700, fontFamily: 'var(--font-mono)', fontSize: 12, color: r.limit === 'REQUIRED' ? 'var(--red)' : 'var(--gold)' }}>{r.id}</td>
                <td style={{ padding: '8px 12px', color: 'var(--text-secondary)' }}>{r.rule}</td>
                <td style={{ padding: '8px 12px', fontWeight: 700, fontFamily: 'var(--font-mono)', fontSize: 12, color: r.limit === 'REQUIRED' ? 'var(--red)' : 'var(--gold-bright)' }}>{r.limit}</td>
                <td style={{ padding: '8px 12px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 11 }}>{r.code}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
