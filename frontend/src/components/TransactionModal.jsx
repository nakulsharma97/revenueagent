export default function TransactionModal({ attempt, onClose }) {
  if (!attempt) return null;
  const tx = attempt.transaction;
  const sub = tx?.subscription;
  const customer = sub?.customer;

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 24 }} onClick={onClose}>
      <div className="panel" style={{ width: '100%', maxWidth: 680, maxHeight: '85vh', overflow: 'auto', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-lg)', background: 'var(--surface)' }} onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Case File — Detail</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 24, fontWeight: 700, color: 'var(--gold)' }}>TXN#{tx?.id || attempt.checkoutSession?.id || attempt.receivable?.id}</div>
            {attempt.batchId && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>BATCH: {attempt.batchId.slice(0, 12)}</div>}
          </div>
          <button onClick={onClose} style={{ background: 'var(--surface-elevated)', border: '1px solid var(--border)', borderRadius: 'var(--radius-full)', width: 32, height: 32, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', fontSize: 14, transition: 'all var(--transition-fast)' }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = 'var(--gold)'; e.currentTarget.style.color = 'var(--gold)'; }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = 'var(--border)'; e.currentTarget.style.color = 'var(--text-muted)'; }}
          >✕</button>
        </div>

        {/* Content */}
        <div style={{ padding: '20px 24px' }}>
          {/* Action & Outcome row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
            {[
              { label: 'Action', value: attempt.actionTaken?.replaceAll('_', ' ') },
              { label: 'Outcome', value: attempt.outcome, color: attempt.outcome === 'SUCCESS' ? 'var(--green)' : attempt.outcome === 'FAILED' ? 'var(--red)' : 'var(--amber)' },
              { label: 'Source', value: attempt.sourceType || 'PAYMENT' },
              { label: 'LLM Driven', value: attempt.llmDriven ? 'YES' : 'NO', color: attempt.llmDriven ? 'var(--gold)' : 'var(--text-muted)' },
            ].map(f => (
              <div key={f.label} style={{ background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', padding: '12px 14px', border: '1px solid var(--border-subtle)' }}>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4, letterSpacing: '0.04em' }}>{f.label}</div>
                <div style={{ fontFamily: 'var(--font-body)', fontWeight: 600, color: f.color || 'var(--text)', fontSize: 13 }}>{f.value}</div>
              </div>
            ))}
          </div>

          {attempt.requiresHumanSignoff && (
            <div style={{ padding: '10px 14px', background: 'var(--red-bg)', border: '1px solid var(--red-border)', borderRadius: 'var(--radius-sm)', marginBottom: 16, fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--red)', fontWeight: 500 }}>
              ⚠ Requires Human Signoff: {attempt.signoffReason || 'No reason provided'}
            </div>
          )}

          {/* Reasoning */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 6, letterSpacing: '0.06em' }}>Agent Reasoning</div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, border: '1px solid var(--border-subtle)' }}>
              "{attempt.reasoning}"
            </div>
          </div>

          {/* Financials */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 16 }}>
            {[
              { label: 'Amount', value: `₹${Number(tx?.amount || attempt.checkoutSession?.cartAmount || attempt.receivable?.invoiceAmount || 0).toLocaleString('en-IN')}` },
              { label: 'Recovered', value: attempt.amountRecovered > 0 ? `₹${Number(attempt.amountRecovered).toLocaleString('en-IN')}` : '—', color: attempt.amountRecovered > 0 ? 'var(--green)' : 'var(--text-muted)' },
              { label: 'Cost', value: attempt.interventionCost > 0 ? `₹${Number(attempt.interventionCost).toFixed(2)}` : '—' },
            ].map(f => (
              <div key={f.label} style={{ background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', padding: '12px 14px', border: '1px solid var(--border-subtle)' }}>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4, letterSpacing: '0.04em' }}>{f.label}</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: f.color || 'var(--text)', fontSize: 18 }}>{f.value}</div>
              </div>
            ))}
          </div>

          {/* Context grid */}
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8, letterSpacing: '0.06em' }}>Context</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '8px 20px', fontSize: 12, fontFamily: 'var(--font-body)' }}>
              {[
                tx?.failureReason && ['Failure reason', tx.failureReason.replaceAll('_', ' ')],
                tx?.retryCount !== undefined && ['Retry count', tx.retryCount],
                ['Status', tx?.status || attempt.checkoutSession?.status || attempt.receivable?.status],
                ['Confidence', `${(attempt.confidence * 100).toFixed(0)}%`],
                sub && ['Plan', sub.planName],
                customer && ['Customer', customer.name],
                customer && ['Reliability', `${(customer.paymentReliabilityScore * 100).toFixed(0)}%`],
                attempt.receivable && ['Days overdue', attempt.receivable.daysOverdue],
              ].filter(Boolean).map(([label, value]) => (
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                  <span style={{ color: 'var(--text)', fontWeight: 500, fontFamily: 'var(--font-mono)', fontSize: 12 }}>{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
