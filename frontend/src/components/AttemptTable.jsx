import { useState, Fragment, useMemo } from 'react';

const ACTION_COLORS = {
  RETRY_NOW: 'var(--ink-green)',
  RETRY_SCHEDULED: 'var(--ink-blue)',
  SEND_PAYMENT_LINK: 'var(--ink-amber)',
  OFFER_DISCOUNT: 'var(--ink-red)',
  ESCALATE_TO_HUMAN: 'var(--ink-purple)',
  ABANDON: 'var(--text-muted)',
};

const OUTCOME_STYLES = {
  SUCCESS: { color: 'var(--ink-green)', bg: 'var(--green-bg)', label: 'SUCCESS' },
  FAILED: { color: 'var(--ink-red)', bg: 'var(--red-bg)', label: 'FAILED' },
  PENDING: { color: 'var(--ink-amber)', bg: 'var(--amber-bg)', label: 'PENDING' },
};

const ACTION_OPTIONS = ['All', 'RETRY_NOW', 'RETRY_SCHEDULED', 'SEND_PAYMENT_LINK', 'OFFER_DISCOUNT', 'ESCALATE_TO_HUMAN', 'ABANDON'];
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
        const batchId = (a.batchId || '').toLowerCase();
        if (!txId.includes(q) && !reasoning.includes(q) && !action.includes(q) && !batchId.includes(q)) return false;
      }
      return true;
    });
  }, [attempts, search, actionFilter, outcomeFilter]);

  if (attempts.length === 0) {
    return (
      <div style={{ padding: '48px 0', textAlign: 'center' }}>
        <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>📋</div>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 16,
          fontWeight: 600,
          color: 'var(--text)',
          marginBottom: 6,
        }}>
          Your decision log will appear here
        </div>
        <div style={{
          fontFamily: 'var(--font-body)',
          fontSize: 13,
          color: 'var(--text-muted)',
        }}>
          Run a batch to start capturing decisions and outcomes
        </div>
      </div>
    );
  }

  const filterStyle = {
    padding: '6px 10px',
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-sm)',
    fontSize: 12,
    fontFamily: 'var(--font-body)',
    color: 'var(--text-secondary)',
    cursor: 'pointer',
    outline: 'none',
  };

  return (
    <div>
      {/* Filters */}
      <div style={{
        padding: '10px 0',
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        flexWrap: 'wrap',
        marginBottom: 12,
      }}>
        <input
          type="text"
          placeholder="Search transaction ID, action, batch…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            ...filterStyle,
            flex: '1 1 200px',
          }}
        />
        <select value={actionFilter} onChange={(e) => setActionFilter(e.target.value)} style={filterStyle}>
          {ACTION_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'All Actions' : o.replaceAll('_', ' ')}</option>
          ))}
        </select>
        <select value={outcomeFilter} onChange={(e) => setOutcomeFilter(e.target.value)} style={filterStyle}>
          {OUTCOME_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'All Outcomes' : o}</option>
          ))}
        </select>
        {filtered.length !== attempts.length && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>
            {filtered.length}/{attempts.length}
          </span>
        )}
      </div>

      {/* Table */}
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
              {['TXN', 'BATCH', 'ACTION', 'SIGNOFF', 'CONF', 'OUTCOME', 'RECOVERED', 'COST', 'LLM'].map((h) => (
                <th key={h} style={{
                  padding: '10px 12px',
                  fontFamily: 'var(--font-body)',
                  fontSize: 11,
                  fontWeight: 600,
                  color: 'var(--text-muted)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                  whiteSpace: 'nowrap',
                }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.slice(0, 200).map((a, idx) => {
              const isOpen = openId === a.id;
              const outcome = OUTCOME_STYLES[a.outcome] || OUTCOME_STYLES.PENDING;
              const actionColor = ACTION_COLORS[a.actionTaken] || 'var(--text-muted)';

              return (
                <Fragment key={a.id}>
                  <tr
                    style={{
                      borderBottom: '1px solid var(--border)',
                      cursor: 'pointer',
                      background: isOpen ? 'var(--surface-hover)' : 'transparent',
                      transition: 'background var(--transition-fast)',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-hover)'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = isOpen ? 'var(--surface-hover)' : 'transparent'; }}
                  >
                    <td
                      style={{ padding: '10px 12px', color: 'var(--ink-blue)', fontWeight: 600, fontFamily: 'var(--font-mono)', fontSize: 12, cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: 2 }}
                      onClick={(e) => { e.stopPropagation(); onSelectAttempt?.(a); }}
                    >
                      #{a.transaction?.id}
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 11, fontFamily: 'var(--font-mono)', maxWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {a.batchId ? a.batchId.slice(0, 8) : '—'}
                    </td>
                    <td
                      style={{ padding: '10px 12px' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 7, height: 7, borderRadius: '50%', background: actionColor, flexShrink: 0 }} />
                        <span style={{ fontFamily: 'var(--font-body)', fontSize: 12 }}>{a.actionTaken?.replaceAll('_', ' ')}</span>
                      </span>
                    </td>
                    <td
                      style={{ padding: '10px 12px' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      {a.requiresHumanSignoff ? (
                        <span style={{
                          color: 'var(--ink-red)',
                          fontWeight: 600,
                          fontSize: 11,
                          fontFamily: 'var(--font-body)',
                        }}>
                          ⚠ YES
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>—</span>
                      )}
                    </td>
                    <td
                      style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 12, fontFamily: 'var(--font-mono)', textAlign: 'right' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      {(a.confidence * 100).toFixed(0)}%
                    </td>
                    <td
                      style={{ padding: '10px 12px' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      <span style={{
                        display: 'inline-block',
                        padding: '2px 8px',
                        borderRadius: 'var(--radius-full)',
                        background: outcome.bg,
                        color: outcome.color,
                        fontWeight: 600,
                        fontSize: 11,
                        fontFamily: 'var(--font-body)',
                      }}>
                        {outcome.label}
                      </span>
                    </td>
                    <td
                      style={{ padding: '10px 12px', color: a.amountRecovered > 0 ? 'var(--ink-green)' : 'var(--text-muted)', fontWeight: a.amountRecovered > 0 ? 600 : 400, textAlign: 'right', fontFamily: 'var(--font-mono)', fontSize: 12 }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      {a.amountRecovered > 0 ? `₹${Number(a.amountRecovered).toLocaleString('en-IN')}` : '—'}
                    </td>
                    <td
                      style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 12, fontFamily: 'var(--font-mono)', textAlign: 'right' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      {a.interventionCost > 0 ? `₹${Number(a.interventionCost).toFixed(2)}` : '—'}
                    </td>
                    <td
                      style={{ padding: '10px 12px', fontSize: 11, fontFamily: 'var(--font-body)', color: a.llmDriven ? 'var(--ink-purple)' : 'var(--text-muted)' }}
                      onClick={() => setOpenId(isOpen ? null : a.id)}
                    >
                      {a.llmDriven ? 'YES' : 'no'}
                    </td>
                  </tr>
                  {isOpen && (
                    <tr>
                      <td colSpan={9} style={{ padding: '0 12px 12px', background: 'var(--surface-hover)' }}>
                        <div style={{
                          marginTop: 8,
                          padding: '12px 16px',
                          background: 'var(--surface)',
                          border: '1px solid var(--border)',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: 12,
                          fontFamily: 'var(--font-body)',
                          color: 'var(--text-secondary)',
                          lineHeight: 1.6,
                        }}>
                          <div style={{ fontSize: 10, color: 'var(--text-muted)', letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 4 }}>
                            Agent Reasoning
                          </div>
                          <div>{a.reasoning}</div>
                          {a.requiresHumanSignoff && a.signoffReason && (
                            <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)', color: 'var(--ink-red)', fontSize: 12, fontWeight: 500 }}>
                              ⚠ Signoff Required: {a.signoffReason}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
      {filtered.length > 200 && (
        <div style={{
          padding: '8px 12px',
          textAlign: 'center',
          fontSize: 12,
          fontFamily: 'var(--font-body)',
          color: 'var(--text-muted)',
          borderTop: '1px solid var(--border)',
        }}>
          Showing 200 of {filtered.length} entries
        </div>
      )}
    </div>
  );
}
