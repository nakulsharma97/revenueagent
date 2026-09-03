import { useState, Fragment, useMemo, memo } from 'react';

const ACTION_COLORS = {
  RETRY_NOW: 'var(--gold)', RETRY_SCHEDULED: 'var(--gold-soft)', RETRY_SILENT: 'var(--text-muted)',
  SEND_PAYMENT_LINK: 'var(--amber)',
  OFFER_DISCOUNT: 'var(--gold-bright)', ESCALATE_TO_HUMAN: 'var(--red)', ABANDON: 'var(--text-muted)',
  CHECKOUT_REMINDER: 'var(--amber)', OFFER_PAYMENT_PLAN: 'var(--green)', SEND_REMINDER: 'var(--gold)',
  PROMISE_FOLLOWUP: 'var(--amber)',
};

const OUTCOME_STYLES = {
  SUCCESS: { color: 'var(--green)', bg: 'var(--green-bg)', label: 'SUCCESS' },
  FAILED: { color: 'var(--red)', bg: 'var(--red-bg)', label: 'FAILED' },
  SKIPPED: { color: 'var(--text-muted)', bg: 'var(--surface-hover)', label: 'SKIPPED' },
  PENDING: { color: 'var(--amber)', bg: 'var(--amber-bg)', label: 'PENDING' },
};

const ACTION_OPTIONS = ['All', 'RETRY_SILENT', 'RETRY_NOW', 'RETRY_SCHEDULED', 'SEND_PAYMENT_LINK', 'OFFER_DISCOUNT', 'ESCALATE_TO_HUMAN', 'ABANDON', 'CHECKOUT_REMINDER', 'OFFER_PAYMENT_PLAN', 'SEND_REMINDER', 'PROMISE_FOLLOWUP', 'NO_ACTION'];
const OUTCOME_OPTIONS = ['All', 'SUCCESS', 'FAILED', 'SKIPPED', 'PENDING'];
const SOURCE_OPTIONS = ['All', 'PAYMENT', 'CHECKOUT', 'RECEIVABLE'];

function AttemptTableInner({ attempts, onSelectAttempt }) {
  const [openId, setOpenId] = useState(null);
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('All');
  const [outcomeFilter, setOutcomeFilter] = useState('All');
  const [sourceFilter, setSourceFilter] = useState('All');

  const filtered = useMemo(() => attempts.filter(a => {
    if (sourceFilter !== 'All' && a.sourceType !== sourceFilter) return false;
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
  }), [attempts, search, actionFilter, outcomeFilter, sourceFilter]);

  if (attempts.length === 0) return (
    <div style={{ padding: '48px 0', textAlign: 'center' }}>
      <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.2 }}>📋</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 16, fontWeight: 600, color: 'var(--text)', marginBottom: 6 }}>Your decision log will appear here</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)' }}>Run a batch to start capturing decisions and outcomes</div>
    </div>
  );

  const filterStyle = { padding: '6px 10px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: 12, fontFamily: 'var(--font-body)', color: 'var(--text-secondary)', cursor: 'pointer', outline: 'none' };

  return (
    <div style={{ width: '100%', minWidth: 0 }}>
      <div style={{ padding: '10px 0', display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 12 }}>
        <input type="text" placeholder="Search transaction ID, action, batch…" value={search} onChange={e => setSearch(e.target.value)} style={{ ...filterStyle, flex: '1 1 200px', color: 'var(--text)', minWidth: 200 }} />
        <select value={sourceFilter} onChange={e => setSourceFilter(e.target.value)} style={filterStyle}>
          {SOURCE_OPTIONS.map(o => <option key={o} value={o}>{o === 'All' ? 'All Sources' : o}</option>)}
        </select>
        <select value={actionFilter} onChange={e => setActionFilter(e.target.value)} style={filterStyle}>
          {ACTION_OPTIONS.map(o => <option key={o} value={o}>{o === 'All' ? 'All Actions' : o.replaceAll('_', ' ')}</option>)}
        </select>
        <select value={outcomeFilter} onChange={e => setOutcomeFilter(e.target.value)} style={filterStyle}>
          {OUTCOME_OPTIONS.map(o => <option key={o} value={o}>{o === 'All' ? 'All Outcomes' : o}</option>)}
        </select>
        {filtered.length !== attempts.length && <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{filtered.length}/{attempts.length}</span>}
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left' }}>
              {['TXN', 'SOURCE', 'BATCH', 'ACTION', 'SIGNOFF', 'CONF', 'OUTCOME', 'RECOVERED', 'COST', 'LLM'].map(h => (
                <th key={h} style={{ padding: '10px 12px', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.slice(0, 200).map(a => {
              const isOpen = openId === a.id;
              const outcome = OUTCOME_STYLES[a.outcome] || OUTCOME_STYLES.PENDING;
              return (
                <Fragment key={a.id}>
                  <tr style={{ borderBottom: '1px solid var(--border-subtle)', cursor: 'pointer', background: isOpen ? 'var(--surface-hover)' : 'transparent', transition: 'background var(--transition-fast)' }}
                    onMouseEnter={e => { e.currentTarget.style.background = 'var(--surface-hover)'; }}
                    onMouseLeave={e => { e.currentTarget.style.background = isOpen ? 'var(--surface-hover)' : 'transparent'; }}>
                    <td style={{ padding: '10px 12px', color: 'var(--gold)', fontWeight: 600, fontFamily: 'var(--font-mono)', fontSize: 12, cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: 2 }} onClick={e => { e.stopPropagation(); onSelectAttempt?.(a); }}>#{a.transaction?.id || a.checkoutSession?.id || a.receivable?.id}</td>
                    <td style={{ padding: '10px 12px' }}><span style={{ padding: '2px 6px', borderRadius: 'var(--radius-full)', background: a.sourceType === 'PAYMENT' ? 'var(--gold-bg)' : a.sourceType === 'CHECKOUT' ? 'var(--amber-bg)' : 'var(--green-bg)', color: a.sourceType === 'PAYMENT' ? 'var(--gold)' : a.sourceType === 'CHECKOUT' ? 'var(--amber)' : 'var(--green)', fontWeight: 600, fontSize: 10, fontFamily: 'var(--font-body)', whiteSpace: 'nowrap' }}>{a.sourceType || 'PAYMENT'}</span></td>
                    <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 11, fontFamily: 'var(--font-mono)', maxWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.batchId ? a.batchId.slice(0, 8) : '—'}</td>
                    <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 7, height: 7, borderRadius: '50%', background: ACTION_COLORS[a.actionTaken] || 'var(--text-muted)', flexShrink: 0 }} />
                        <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-secondary)' }}>{a.actionTaken?.replaceAll('_', ' ')}{a.discountPercent ? ` · ${a.discountPercent}%` : ''}</span>
                        {!a.customerNotified && a.actionTaken !== 'ESCALATE_TO_HUMAN' && a.actionTaken !== 'ABANDON' && (
                          <span style={{ padding: '1px 5px', borderRadius: 'var(--radius-full)', background: 'var(--surface-hover)', border: '1px solid var(--border)', fontSize: 9, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.04em' }}>SILENT</span>
                        )}
                        {a.decisionSource === 'RECOVERY_INTELLIGENCE_ENGINE' && (
                          <span title={`Decision source: ${a.decisionSource.replaceAll('_', ' ')}`} style={{ padding: '1px 5px', borderRadius: 'var(--radius-full)', background: 'var(--green-bg)', color: 'var(--green)', fontSize: 9, fontWeight: 700, letterSpacing: '0.03em', whiteSpace: 'nowrap' }}>{a.engineVersion ? a.engineVersion.replace('RECOVERY_INTELLIGENCE_', 'ENGINE ') : 'ENGINE'}</span>
                        )}
                      </span>
                    </td>
                    <td style={{ padding: '10px 12px' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      {a.requiresHumanSignoff ? <span style={{ color: 'var(--red)', fontWeight: 600, fontSize: 11 }}>⚠ YES</span> : <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>—</span>}
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 12, fontFamily: 'var(--font-mono)', textAlign: 'right' }} onClick={() => setOpenId(isOpen ? null : a.id)}>{(a.confidence * 100).toFixed(0)}%</td>
                    <td style={{ padding: '10px 12px' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 'var(--radius-full)', background: outcome.bg, color: outcome.color, fontWeight: 600, fontSize: 11, whiteSpace: 'nowrap' }}>{outcome.label}</span>
                    </td>
                    <td style={{ padding: '10px 12px', color: a.amountRecovered > 0 ? 'var(--green)' : 'var(--text-muted)', fontWeight: a.amountRecovered > 0 ? 600 : 400, textAlign: 'right', fontFamily: 'var(--font-mono)', fontSize: 12, whiteSpace: 'nowrap' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      {a.amountRecovered > 0 ? `₹${Number(a.amountRecovered).toLocaleString('en-IN')}` : '—'}
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: 12, fontFamily: 'var(--font-mono)', textAlign: 'right', whiteSpace: 'nowrap' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      {a.interventionCost > 0 ? `₹${Number(a.interventionCost).toFixed(2)}` : '—'}
                    </td>
                    <td style={{ padding: '10px 12px', fontSize: 11, color: a.llmDriven ? 'var(--gold)' : 'var(--text-muted)' }} onClick={() => setOpenId(isOpen ? null : a.id)}>
                      {a.llmDriven ? 'YES' : 'no'}
                    </td>
                  </tr>
                  {isOpen && (
                    <tr><td colSpan={10} style={{ padding: '0 12px 12px', background: 'var(--surface-hover)' }}>
                      <div style={{ marginTop: 8, padding: '12px 16px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: 12, fontFamily: 'var(--font-body)', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)', letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 4 }}>Agent Reasoning</div>
                        <div>{a.reasoning}</div>
                        {(a.recoveryState || a.fatigueScore > 0) && (
                          <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                            {a.recoveryState && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: 'var(--gold-bg)', color: 'var(--gold)', fontSize: 10, fontWeight: 700 }}>STATE · {a.recoveryState.replaceAll('_', ' ')}</span>}
                            {a.fatigueScore > 0 && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: a.fatigueScore >= 0.6 ? 'var(--red-bg)' : 'var(--amber-bg)', color: a.fatigueScore >= 0.6 ? 'var(--red)' : 'var(--amber)', fontSize: 10, fontWeight: 700 }}>FATIGUE · {(a.fatigueScore * 100).toFixed(0)}%</span>}
                          </div>
                        )}
                        {(a.decisionSource || a.engineVersion || a.fallbackReason) && (
                          <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                            {a.decisionSource && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: 'var(--green-bg)', color: 'var(--green)', fontSize: 10, fontWeight: 700 }}>SOURCE · {a.decisionSource.replaceAll('_', ' ')}</span>}
                            {a.engineVersion && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: 'var(--surface-hover)', border: '1px solid var(--border)', color: 'var(--text-secondary)', fontSize: 10, fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{a.engineVersion}</span>}
                            {a.fallbackReason && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: 'var(--amber-bg)', color: 'var(--amber)', fontSize: 10, fontWeight: 600 }}>FALLBACK · {a.fallbackReason}</span>}
                          </div>
                        )}
                        {a.requiresHumanSignoff && a.signoffReason && (
                          <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)', color: 'var(--red)', fontSize: 12, fontWeight: 500 }}>⚠ Signoff Required: {a.signoffReason}</div>
                        )}
                        {a.decisionTrace && a.decisionTrace.length > 0 && (
                          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
                            <div style={{ fontSize: 10, color: 'var(--text-muted)', letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 8 }}>Full Decision Trace</div>
                            <div style={{ position: 'relative', paddingLeft: 20 }}>
                              {a.decisionTrace.map((step, i) => (
                                <div key={i} style={{ position: 'relative', paddingBottom: i < a.decisionTrace.length - 1 ? 12 : 0 }}>
                                  <div style={{ position: 'absolute', left: -16, top: 4, width: 8, height: 8, borderRadius: '50%', background: step.step === 'EXECUTION' ? (step.detail.includes('SUCCESS') ? 'var(--green)' : 'var(--red)') : step.step === 'SIGNOFF' ? 'var(--red)' : 'var(--gold)', border: '1px solid var(--surface)' }} />
                                  {i < a.decisionTrace.length - 1 && <div style={{ position: 'absolute', left: -13, top: 14, width: 1, height: 'calc(100% - 10px)', background: 'var(--border)' }} />}
                                  <div style={{ fontSize: 10, fontWeight: 600, color: 'var(--gold)', letterSpacing: '0.04em', textTransform: 'uppercase', marginBottom: 2 }}>{step.step}</div>
                                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.5 }}>{step.detail}</div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                        {a.customerMessage && (
                          <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
                            <div style={{ fontSize: 10, color: 'var(--text-muted)', letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 4 }}>Customer-Facing Message</div>
                            <div style={{ padding: '8px 12px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', fontStyle: 'italic', fontSize: 12, lineHeight: 1.6, color: 'var(--text-secondary)' }}>
                              {a.customerMessage}
                            </div>
                          </div>
                        )}
                      </div>
                    </td></tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
      {filtered.length > 200 && <div style={{ padding: '8px 12px', textAlign: 'center', fontSize: 12, color: 'var(--text-muted)', borderTop: '1px solid var(--border)' }}>Showing 200 of {filtered.length} entries</div>}
    </div>
  );
}

export default memo(AttemptTableInner, (prev, next) => prev.attempts === next.attempts && prev.onSelectAttempt === next.onSelectAttempt);
