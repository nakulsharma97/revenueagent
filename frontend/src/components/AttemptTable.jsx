import { useState, Fragment, useMemo } from 'react';

const ACTION_COLORS = {
  RETRY_NOW: 'var(--ink-green)',
  RETRY_SCHEDULED: '#4CAF50',
  SEND_PAYMENT_LINK: 'var(--ink-amber)',
  OFFER_DISCOUNT: 'var(--ink-red)',
  ESCALATE_TO_HUMAN: 'var(--ink-purple)',
  ABANDON: 'var(--text-muted)',
};

const OUTCOME_STYLES = {
  SUCCESS: { color: 'var(--ink-green)', label: '✓ OK' },
  FAILED: { color: 'var(--ink-red)', label: '✕ FAIL' },
  PENDING: { color: 'var(--ink-amber)', label: '◇ SKIP' },
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
      <div className="panel" style={{ padding: 48, textAlign: 'center' }}>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--text-muted)' }}>
          NO DATA — RUN A BATCH TO POPULATE THE LEDGER
        </div>
      </div>
    );
  }

  const filterStyle = {
    padding: '5px 8px',
    background: 'var(--surface)',
    border: 'var(--rule)',
    borderRadius: 0,
    fontSize: 11,
    fontFamily: 'var(--font-mono)',
    color: 'var(--text-secondary)',
    cursor: 'pointer',
  };

  return (
    <div className="panel" style={{ overflow: 'hidden' }}>
      {/* Filters */}
      <div style={{
        padding: '8px 12px',
        borderBottom: 'var(--rule)',
        background: 'var(--surface-2)',
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        flexWrap: 'wrap',
      }}>
        <input
          type="text"
          placeholder="SEARCH TXN ID, ACTION, BATCH…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            ...filterStyle,
            flex: '1 1 200px',
            fontSize: 11,
            letterSpacing: '0.02em',
          }}
        />
        <select value={actionFilter} onChange={(e) => setActionFilter(e.target.value)} style={filterStyle}>
          {ACTION_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'ALL ACTIONS' : o.replaceAll('_', ' ')}</option>
          ))}
        </select>
        <select value={outcomeFilter} onChange={(e) => setOutcomeFilter(e.target.value)} style={filterStyle}>
          {OUTCOME_OPTIONS.map((o) => (
            <option key={o} value={o}>{o === 'All' ? 'ALL OUTCOMES' : o}</option>
          ))}
        </select>
        {filtered.length !== attempts.length && (
          <span style={{ fontSize: 10, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', letterSpacing: '0.04em' }}>
            {filtered.length}/{attempts.length}
          </span>
        )}
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12, fontFamily: 'var(--font-mono)' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid var(--text)', textAlign: 'left' }}>
            {['TXN', 'BATCH', 'ACTION', 'SIGNOFF', 'CONF', 'OUTCOME', 'RECOVERED', 'COST', 'LLM'].map((h) => (
              <th key={h} style={{
                padding: '7px 10px',
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
          {filtered.slice(0, 200).map((a, idx) => {
            const isOpen = openId === a.id;
            const outcome = OUTCOME_STYLES[a.outcome] || OUTCOME_STYLES.PENDING;
            const actionColor = ACTION_COLORS[a.actionTaken] || 'var(--text-muted)';
            const isEven = idx % 2 === 0;

            return (
              <Fragment key={a.id}>
                <tr
                  style={{
                    borderBottom: 'var(--rule)',
                    cursor: 'pointer',
                    background: isOpen ? 'var(--surface-hover)' : isEven ? 'transparent' : 'rgba(0,0,0,0.015)',
                    transition: 'background var(--transition-fast)',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-hover)'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = isOpen ? 'var(--surface-hover)' : isEven ? 'transparent' : 'rgba(0,0,0,0.015)'; }}
                >
                  <td
                    style={{ padding: '7px 10px', color: 'var(--ink-blue)', fontWeight: 500, cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: 2 }}
                    onClick={(e) => { e.stopPropagation(); onSelectAttempt?.(a); }}
                  >
                    #{a.transaction?.id}
                  </td>
                  <td style={{ padding: '7px 10px', color: 'var(--text-muted)', fontSize: 9, maxWidth: 60, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {a.batchId ? a.batchId.slice(0, 8) : '—'}
                  </td>
                  <td
                    style={{ padding: '7px 10px' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                      <span style={{ width: 5, height: 5, background: actionColor, flexShrink: 0 }} />
                      <span>{a.actionTaken?.replaceAll('_', ' ')}</span>
                    </span>
                  </td>
                  <td
                    style={{ padding: '7px 10px' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.requiresHumanSignoff ? (
                      <span style={{ color: 'var(--ink-red)', fontWeight: 600, fontSize: 10 }}>
                        ⚠ YES
                      </span>
                    ) : (
                      <span style={{ color: 'var(--text-muted)', fontSize: 10 }}>—</span>
                    )}
                  </td>
                  <td
                    style={{ padding: '7px 10px', color: 'var(--text-muted)', fontSize: 11, textAlign: 'right' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {(a.confidence * 100).toFixed(0)}%
                  </td>
                  <td
                    style={{ padding: '7px 10px' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    <span style={{ color: outcome.color, fontWeight: 500, fontSize: 11 }}>
                      {outcome.label}
                    </span>
                  </td>
                  <td
                    style={{ padding: '7px 10px', color: a.amountRecovered > 0 ? 'var(--ink-green)' : 'var(--text-muted)', fontWeight: a.amountRecovered > 0 ? 600 : 400, textAlign: 'right' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.amountRecovered > 0 ? `₹${Number(a.amountRecovered).toLocaleString('en-IN')}` : '—'}
                  </td>
                  <td
                    style={{ padding: '7px 10px', color: 'var(--text-muted)', fontSize: 11, textAlign: 'right' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.interventionCost > 0 ? `₹${Number(a.interventionCost).toFixed(2)}` : '—'}
                  </td>
                  <td
                    style={{ padding: '7px 10px', fontSize: 10, color: a.llmDriven ? 'var(--ink-purple)' : 'var(--text-muted)' }}
                    onClick={() => setOpenId(isOpen ? null : a.id)}
                  >
                    {a.llmDriven ? 'YES' : 'no'}
                  </td>
                </tr>
                {isOpen && (
                  <tr>
                    <td colSpan={9} style={{ padding: '0 10px 10px', background: 'var(--surface-hover)' }}>
                      <div style={{
                        marginTop: 6,
                        padding: '10px 12px',
                        background: 'var(--surface)',
                        border: 'var(--rule)',
                        fontSize: 12,
                        fontFamily: 'var(--font-mono)',
                        color: 'var(--text-secondary)',
                        lineHeight: 1.6,
                      }}>
                        <span style={{ fontSize: 9, color: 'var(--text-muted)', letterSpacing: '0.1em', textTransform: 'uppercase' }}>
                          AGENT REASONING
                        </span>
                        <div style={{ marginTop: 4 }}>
                          {a.reasoning}
                        </div>
                        {a.requiresHumanSignoff && a.signoffReason && (
                          <div style={{ marginTop: 6, paddingTop: 6, borderTop: 'var(--rule)', color: 'var(--ink-red)', fontSize: 11 }}>
                            ⚠ SIGNOFF REQUIRED: {a.signoffReason}
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
      {filtered.length > 200 && (
        <div style={{
          padding: '6px 12px',
          textAlign: 'center',
          fontSize: 11,
          fontFamily: 'var(--font-mono)',
          color: 'var(--text-muted)',
          borderTop: 'var(--rule)',
          background: 'var(--surface-2)',
        }}>
          SHOWING 200 OF {filtered.length} ENTRIES
        </div>
      )}
    </div>
  );
}
