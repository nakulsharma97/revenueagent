import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

export default function PendingReview() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API_BASE}/api/recovery/pending-review`)
      .then(r => r.json())
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="panel" style={{ padding: '16px 20px' }}>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
          Loading pending review queue…
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return null; // Don't show empty panel
  }

  return (
    <div className="panel" style={{ border: '1px solid var(--ink-red)' }}>
      <div style={{
        padding: '10px 16px',
        borderBottom: '1px solid var(--ink-red)',
        background: 'var(--red-bg)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{
            fontFamily: 'var(--font-display)',
            fontSize: 12,
            fontWeight: 700,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: 'var(--ink-red)',
          }}>
            ⚠ Pending Human Review
          </span>
          <span style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 10,
            color: 'var(--ink-red)',
            border: '1px solid var(--ink-red)',
            padding: '1px 6px',
          }}>
            {items.length} ITEMS
          </span>
        </div>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-muted)' }}>
          BOUNDED WORKFLOW — ESCALATED PER RULES
        </span>
      </div>

      <div style={{ maxHeight: 200, overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'var(--font-mono)' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left' }}>
              {['TXN', 'ACTION', 'OUTCOME', 'REASON'].map(h => (
                <th key={h} style={{ padding: '6px 10px', fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.map((a) => (
              <tr key={a.id} style={{ borderBottom: '1px solid var(--border)' }}>
                <td style={{ padding: '6px 10px', color: 'var(--ink-blue)' }}>
                  #{a.transaction?.id}
                </td>
                <td style={{ padding: '6px 10px', color: 'var(--text)' }}>
                  {a.actionTaken?.replaceAll('_', ' ')}
                </td>
                <td style={{ padding: '6px 10px', color: a.outcome === 'SUCCESS' ? 'var(--ink-green)' : a.outcome === 'FAILED' ? 'var(--ink-red)' : 'var(--ink-amber)' }}>
                  {a.outcome}
                </td>
                <td style={{ padding: '6px 10px', color: 'var(--ink-red)', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {a.signoffReason || 'No reason provided'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
