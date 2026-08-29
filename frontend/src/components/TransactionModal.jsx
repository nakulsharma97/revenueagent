export default function TransactionModal({ attempt, onClose }) {
  if (!attempt) return null;

  const tx = attempt.transaction;
  const sub = tx?.subscription;
  const customer = sub?.customer;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        backdropFilter: 'blur(4px)',
      }}
      onClick={onClose}
    >
      <div
        className="card"
        style={{
          width: 520,
          maxHeight: '80vh',
          overflow: 'auto',
          padding: 0,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{
          padding: '20px 24px',
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600, marginBottom: 4 }}>
              Transaction Detail
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 20, fontWeight: 700, color: 'var(--text)' }}>
              #{tx?.id}
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'var(--surface-2)',
              border: '1px solid var(--border)',
              borderRadius: 6,
              width: 32,
              height: 32,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-muted)',
              fontSize: 16,
            }}
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: '20px 24px' }}>
          {/* Action & Outcome */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
            <div style={{ flex: 1, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 8 }}>
              <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Action Taken</div>
              <div style={{ fontWeight: 600, color: 'var(--text)' }}>{attempt.actionTaken?.replaceAll('_', ' ')}</div>
            </div>
            <div style={{ flex: 1, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 8 }}>
              <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Outcome</div>
              <div style={{
                fontWeight: 600,
                color: attempt.outcome === 'SUCCESS' ? 'var(--green)' : attempt.outcome === 'FAILED' ? 'var(--red)' : 'var(--amber)',
              }}>
                {attempt.outcome}
              </div>
            </div>
          </div>

          {/* Reasoning */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6, fontWeight: 600 }}>Agent Reasoning</div>
            <div style={{
              padding: '12px 14px',
              background: 'var(--surface-2)',
              borderRadius: 8,
              fontSize: 13,
              color: 'var(--text-secondary)',
              lineHeight: 1.6,
              fontStyle: 'italic',
            }}>
              "{attempt.reasoning}"
            </div>
          </div>

          {/* Financials */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
            <div style={{ flex: 1, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 8 }}>
              <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Amount</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text)', fontSize: 16 }}>₹{Number(tx?.amount || 0).toLocaleString('en-IN')}</div>
            </div>
            <div style={{ flex: 1, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 8 }}>
              <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Recovered</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: attempt.amountRecovered > 0 ? 'var(--green)' : 'var(--text-muted)', fontSize: 16 }}>
                {attempt.amountRecovered > 0 ? `₹${Number(attempt.amountRecovered).toLocaleString('en-IN')}` : '—'}
              </div>
            </div>
            <div style={{ flex: 1, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 8 }}>
              <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Cost</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text-muted)', fontSize: 16 }}>
                {attempt.interventionCost > 0 ? `₹${Number(attempt.interventionCost).toFixed(2)}` : '—'}
              </div>
            </div>
          </div>

          {/* Transaction Context */}
          <div style={{ borderTop: '1px solid var(--border)', paddingTop: 16 }}>
            <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10, fontWeight: 600 }}>Transaction Context</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px', fontSize: 13 }}>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Failure reason: </span>
                <span style={{ color: 'var(--text)', fontWeight: 500 }}>{tx?.failureReason?.replaceAll('_', ' ')}</span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Retry count: </span>
                <span style={{ color: 'var(--text)', fontWeight: 500 }}>{tx?.retryCount}</span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Status: </span>
                <span style={{ color: 'var(--text)', fontWeight: 500 }}>{tx?.status}</span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Confidence: </span>
                <span style={{ color: 'var(--text)', fontWeight: 500 }}>{(attempt.confidence * 100).toFixed(0)}%</span>
              </div>
              {sub && (
                <>
                  <div>
                    <span style={{ color: 'var(--text-muted)' }}>Plan: </span>
                    <span style={{ color: 'var(--text)', fontWeight: 500 }}>{sub.planName}</span>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-muted)' }}>Billing: </span>
                    <span style={{ color: 'var(--text)', fontWeight: 500 }}>{sub.billingCycle}</span>
                  </div>
                </>
              )}
              {customer && (
                <>
                  <div>
                    <span style={{ color: 'var(--text-muted)' }}>Customer: </span>
                    <span style={{ color: 'var(--text)', fontWeight: 500 }}>{customer.name}</span>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-muted)' }}>Reliability: </span>
                    <span style={{ color: 'var(--text)', fontWeight: 500 }}>{(customer.paymentReliabilityScore * 100).toFixed(0)}%</span>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
