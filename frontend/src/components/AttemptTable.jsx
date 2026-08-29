import { useState, Fragment, useMemo } from 'react';

const ACTION_COLORS = {
  RETRY_NOW: '#059669',
  RETRY_SCHEDULED: '#34D399',
  SEND_PAYMENT_LINK: '#D97706',
  OFFER_DISCOUNT: '#DC2626',
  ESCALATE_TO_HUMAN: '#6366F1',
  ABANDON: '#6B7280',
};

const OUTCOME_STYLES = {
  SUCCESS: { color: '#059669', bg: '#ECFDF5', border: '#A7F3D0', label: 'Success' },
  FAILED: { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', label: 'Failed' },
  PENDING: { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', label: 'Pending' },
};

const ACTION_OPTIONS = ['All', 'RETRY_NOW', 'RETRY_SCHEDULED', 'SEND_PAYMENT_LINK', 'OFFER_DISCOUNT', 'ESCALATE_TO_HUMAN'];
const OUTCOME_OPTIONS = ['All', 'SUCCESS', 'FAILED', 'PENDING'];

export default function AttemptTable({ attempts, onSelectAttempt }) {
  const [openId, setOpenId] = useState(null);
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('All');
  const [outcomeFilter, setOutcomeFilter] = useState('All');

  const filtered = useMemo(() => {
    return attempts.filter((a) => {
      if (actionFilter !== 'All' && a.actionTaken !== actionFilter) return false;
      if (outcomeFilter !== 'All' && a.outcome !== outcomeFilter) return false;
      if (search) {
        const q = search.toLowerCase();
        const txId = String(a.transaction?.id || '');
        const reasoning = (a.reasoning || '').toLowerCase();
        const action = (a.actionTaken || '').toLowerCase();
        if (!txId.includes(q) && !reasoning.includes(q) && !action.includes(q)) return false;
      }
      return true;
    });
  }, [attempts, search, actionFilter, outcomeFilter]);

  if (attempts.length === 0) {
    return (
      <div className="card" style={{ padding: 48, textAlign: 'center' }}>
        <div style={{ fontSize: 32, marginBottom: 12, opacity: 0.3 }}>📋</div>
        <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 4 }}>
          No recovery attempts yet
        </div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Run a batch to see the agent's decisions and reasoning here.
        </div>
      </div>
    );
  }

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      {/* Filters bar */}
      <div style={{
        padding: '12px 16px',
        borderBottom: '1px solid var(--border)',
        background: 'var(--surface-2)',
        display: 'flex',
        gap: 10,
        alignItems: 'center',
        flexWrap: 'wrap',
      }}>
        {/* Search */}
        <div style={{ position: 'relative', flex: '1 1 200px' }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" strokeWidth="2" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}>
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            type="text"
            placeholder="Search by txn ID, action, reasoning…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              width: '100%',
              padding: '7px 12px 7px 32px',
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 6,
              fontSize: 12,
              color: 'var(--text)',
              outline: 'none',
              fontFamily: 'var(--font-body)',
            }}
          />
        </div>

        {/* Action filter */}
        <select
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          style={{
            padding: '7px 10px',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 6,
            fontSize: 12,
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            fontFamily: 'var(--font-body)',
          }}
        >
          {ACTION_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'All actions' : o.replaceAll('_', ' ')}</option>
          ))}
        </select>

        {/* Outcome filter */}
        <select
          value={outcomeFilter}
          onChange={(e) => setOutcomeFilter(e.target.value)}
          style={{
            padding: '7px 10px',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 6,
            fontSize: 12,
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            fontFamily: 'var(--font-body)',
          }}
        >
          {OUTCOME_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'All outcomes' : o}</option>
          ))}
        </select>

        {filtered.length !== attempts.length && (
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
            {filtered.length} of {attempts.length}
          </span>
        )}
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr style={{
            background: 'var(--surface-2)',
            textAlign: 'left',
            borderBottom: '1px solid var(--border)',
          }}>
            {['Txn', 'Action', 'Confidence', 'Outcome', 'Recovered', 'Cost'].map((h) => (
              <th key={h} style={{
                padding: '10px 16px',
                color: 'var(--text-muted)',
                fontWeight: 600,
                fontSize: 11,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
              }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filtered.slice(0, 200).map((a) => {
            const isOpen = openId === a.id;
            const outcome = OUTCOME_STYLES[a.outcome] || OUTCOME_STYLES.PENDING;
            const actionColor = ACTION_COLORS[a.actionTaken] || 'var(--text-muted)';

            return (
              <Fragment key={a.id}>
                <tr
                  style={{
                    borderTop: '1px solid var(--border)',
                    cursor: 'pointer',
                    background: isOpen ? 'var(--surface-hover)' : 'transparent',
                    transition: 'background 150ms ease',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-hover)'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = isOpen ? 'var(--surface-hover)' : 'transparent'; }}
                >
                  <td
                    style={{ padding: '10px 16px', fontFamily: 'var(--font-mono)', color: 'var(--teal)', fontWeight: 500, cursor: 'pointer', textDecoration: 'underline', textDecorationStyle: 'dotted', textUnderlineOffset: 3 }}
                    onClick={(e) => { e.stopPropagation(); onSelectAttempt?.(a); }}
                  >
                    #{a.transaction?.id}
                  </td>
                  <td
                    style={{ padding: '10px 16px' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 6, height: 6, borderRadius: '50%', background: actionColor, flexShrink: 0 }} />
                      <span style={{ color: 'var(--text)' }}>{a.actionTaken?.replaceAll('_', ' ')}</span>
                    </span>
                  </td>
                  <td
                    style={{ padding: '10px 16px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)', fontSize: 12 }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {(a.confidence * 100).toFixed(0)}%
                  </td>
                  <td
                    style={{ padding: '10px 16px' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    <span style={{
                      display: 'inline-block',
                      padding: '2px 8px',
                      borderRadius: 4,
                      background: outcome.bg,
                      border: `1px solid ${outcome.border}`,
                      color: outcome.color,
                      fontFamily: 'var(--font-mono)',
                      fontSize: 11,
                      fontWeight: 500,
                    }}>
                      {outcome.label}
                    </span>
                  </td>
                  <td
                    style={{ padding: '10px 16px', fontFamily: 'var(--font-mono)', color: a.amountRecovered > 0 ? 'var(--green)' : 'var(--text-muted)', fontWeight: a.amountRecovered > 0 ? 600 : 400 }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.amountRecovered > 0 ? `₹${Number(a.amountRecovered).toLocaleString('en-IN')}` : '—'}
                  </td>
                  <td
                    style={{ padding: '10px 16px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)', fontSize: 12 }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.interventionCost > 0 ? `₹${Number(a.interventionCost).toFixed(2)}` : '—'}
                  </td>
                </tr>
                {isOpen && (
                  <tr>
                    <td colSpan={6} style={{ padding: '0 16px 12px', background: 'var(--surface-hover)' }}>
                      <div style={{
                        marginTop: 8,
                        padding: '12px 16px',
                        background: 'var(--surface)',
                        border: '1px solid var(--border)',
                        borderRadius: 8,
                        fontSize: 13,
                        color: 'var(--text-secondary)',
                        lineHeight: 1.6,
                      }}>
                        <span style={{
                          fontSize: 10,
                          color: 'var(--text-muted)',
                          fontFamily: 'var(--font-mono)',
                          textTransform: 'uppercase',
                          letterSpacing: '0.06em',
                          display: 'block',
                          marginBottom: 4,
                        }}>
                          Agent reasoning
                        </span>
                        {a.reasoning}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
      {filtered.length > 200 && (
        <div style={{
          padding: '10px 16px',
          textAlign: 'center',
          fontSize: 12,
          color: 'var(--text-muted)',
          borderTop: '1px solid var(--border)',
          background: 'var(--surface-2)',
        }}>
          Showing 200 of {filtered.length} attempts
        </div>
      )}
    </div>
  );
}
