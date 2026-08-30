import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

export default function PendingReview({ forceShow }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API_BASE}/api/recovery/pending-review`)
      .then(r => r.json()).then(setItems).catch(() => setItems([])).finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="card" style={{ padding: '16px 20px', width: '100%', minWidth: 0 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Loading pending review queue…</div>
    </div>
  );

  if (items.length === 0 && !forceShow) return null;

  if (items.length === 0 && forceShow) return (
    <div className="card" style={{ width: '100%', minWidth: 0 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>PENDING HUMAN REVIEW</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)' }}>No items currently require human sign-off. Run a batch to check.</div>
    </div>
  );

  return (
    <div className="card" style={{ border: '1px solid var(--red-border)', background: 'var(--surface)', width: '100%', minWidth: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--red)' }}>⚠ Pending Human Review</span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color: 'var(--red)', background: 'var(--red-bg)', border: '1px solid var(--red-border)', borderRadius: 'var(--radius-full)', padding: '2px 10px' }}>{items.length} ITEMS</span>
        </div>
        <span style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)' }}>Bounded Workflow — Escalated Per Rules</span>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left' }}>
              {['TXN', 'SOURCE', 'ACTION', 'OUTCOME', 'REASON'].map(h => (
                <th key={h} style={{ padding: '8px 10px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.slice(0, 50).map(a => (
              <tr key={a.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                <td style={{ padding: '8px 10px', color: 'var(--gold)', fontFamily: 'var(--font-mono)', fontWeight: 600, fontSize: 12 }}>#{a.transaction?.id || a.checkoutSession?.id || a.receivable?.id}</td>
                <td style={{ padding: '8px 10px' }}>
                  <span style={{ padding: '2px 6px', borderRadius: 'var(--radius-full)', background: a.sourceType === 'PAYMENT' ? 'var(--gold-bg)' : a.sourceType === 'CHECKOUT' ? 'var(--amber-bg)' : 'var(--green-bg)', color: a.sourceType === 'PAYMENT' ? 'var(--gold)' : a.sourceType === 'CHECKOUT' ? 'var(--amber)' : 'var(--green)', fontWeight: 600, fontSize: 10 }}>{a.sourceType || 'PAYMENT'}</span>
                </td>
                <td style={{ padding: '8px 10px', color: 'var(--text-secondary)' }}>{a.actionTaken?.replaceAll('_', ' ')}</td>
                <td style={{ padding: '8px 10px' }}>
                  <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 'var(--radius-full)', background: a.outcome === 'SUCCESS' ? 'var(--green-bg)' : a.outcome === 'FAILED' ? 'var(--red-bg)' : 'var(--amber-bg)', color: a.outcome === 'SUCCESS' ? 'var(--green)' : a.outcome === 'FAILED' ? 'var(--red)' : 'var(--amber)', fontWeight: 600, fontSize: 11 }}>{a.outcome}</span>
                </td>
                <td style={{ padding: '8px 10px', color: 'var(--red)', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.signoffReason || 'No reason provided'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
