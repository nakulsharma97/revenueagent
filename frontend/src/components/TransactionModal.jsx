import { useEffect, useState } from 'react';
import { fetchCounterfactuals, fetchTimeline } from '../api';

const fmt = (v) => (v === null || v === undefined ? '—' : `₹${Number(v).toLocaleString('en-IN')}`);
const pct = (v) => (v === null || v === undefined ? '—' : `${(v * 100).toFixed(0)}%`);

export default function TransactionModal({ attempt, onClose }) {
  const [counterfactuals, setCounterfactuals] = useState(null);
  const [timeline, setTimeline] = useState(null);

  // State starts null (modal shows "no data yet") and only flips once data arrives —
  // setting it synchronously here made the modal flash empty on every reopen. The
  // modal is keyed by attempt in App.jsx, so a new case remounts and stale rows vanish.
  useEffect(() => {
    const src = attempt && (attempt.transaction ? { t: 'PAYMENT', id: attempt.transaction.id }
      : attempt.checkoutSession ? { t: 'CHECKOUT', id: attempt.checkoutSession.id }
      : attempt.receivable ? { t: 'RECEIVABLE', id: attempt.receivable.id } : null);
    if (!src || !src.id || !attempt.actionTaken || attempt.actionTaken === 'NO_ACTION') return;
    let alive = true;
    Promise.all([
      fetchCounterfactuals(src.t, src.id).catch(() => []),
      fetchTimeline(src.t, src.id).catch(() => []),
    ]).then(([c, t]) => { if (alive) { setCounterfactuals(c); setTimeline(t); } });
    return () => { alive = false; };
  }, [attempt]);

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

          {/* Recovery Intelligence chips */}
          {(attempt.recoveryState || attempt.fatigueScore > 0 || attempt.discountPercent != null) && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
              {attempt.recoveryState && (
                <span style={{ padding: '4px 10px', borderRadius: 'var(--radius-full)', background: attempt.recoveryState === 'STOP_INTERVENTION' ? 'var(--red-bg)' : attempt.recoveryState === 'RECOVERY_FATIGUE' ? 'var(--amber-bg)' : 'var(--gold-bg)', color: attempt.recoveryState === 'STOP_INTERVENTION' ? 'var(--red)' : attempt.recoveryState === 'RECOVERY_FATIGUE' ? 'var(--amber)' : 'var(--gold)', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700 }}>
                  STATE · {attempt.recoveryState.replaceAll('_', ' ')}
                </span>
              )}
              {attempt.fatigueScore > 0 && (
                <span style={{ padding: '4px 10px', borderRadius: 'var(--radius-full)', background: attempt.fatigueScore >= 0.6 ? 'var(--red-bg)' : attempt.fatigueScore >= 0.3 ? 'var(--amber-bg)' : 'var(--surface-hover)', border: '1px solid var(--border)', color: attempt.fatigueScore >= 0.6 ? 'var(--red)' : attempt.fatigueScore >= 0.3 ? 'var(--amber)' : 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 700 }}>
                  FATIGUE · {(attempt.fatigueScore * 100).toFixed(0)}%
                </span>
              )}
              {attempt.discountPercent != null && (
                <span style={{ padding: '4px 10px', borderRadius: 'var(--radius-full)', background: 'var(--gold-bg)', color: 'var(--gold)', fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 700 }}>
                  {attempt.discountPercent}% DISCOUNT APPLIED
                </span>
              )}
              {attempt.llmDriven && (
                <span style={{ padding: '4px 10px', borderRadius: 'var(--radius-full)', background: 'var(--surface-hover)', border: '1px solid var(--border)', color: 'var(--gold)', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700 }}>
                  LLM EXPLAINED
                </span>
              )}
            </div>
          )}

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

          {/* Decision Trace Timeline */}
          {attempt.decisionTrace && attempt.decisionTrace.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8, letterSpacing: '0.06em' }}>Decision Trace</div>
              <div style={{ padding: '14px 16px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                <div style={{ position: 'relative', paddingLeft: 20 }}>
                  {attempt.decisionTrace.map((step, i) => (
                    <div key={i} style={{ position: 'relative', paddingBottom: i < attempt.decisionTrace.length - 1 ? 14 : 0 }}>
                      <div style={{ position: 'absolute', left: -16, top: 4, width: 8, height: 8, borderRadius: '50%', background: step.step === 'EXECUTION' ? (step.detail.includes('SUCCESS') ? 'var(--green)' : 'var(--red)') : step.step === 'SIGNOFF' ? 'var(--red)' : 'var(--gold)', border: '1.5px solid var(--surface)' }} />
                      {i < attempt.decisionTrace.length - 1 && <div style={{ position: 'absolute', left: -13, top: 14, width: 1, height: 'calc(100% - 10px)', background: 'var(--border)' }} />}
                      <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--gold)', letterSpacing: '0.05em', textTransform: 'uppercase', marginBottom: 3 }}>{step.step}</div>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.5, fontFamily: 'var(--font-body)' }}>{step.detail}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* Customer-facing message */}
          {attempt.customerMessage && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 6, letterSpacing: '0.06em' }}>Customer-Facing Message</div>
              <div style={{ padding: '12px 14px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, border: '1px solid var(--border-subtle)', fontStyle: 'italic' }}>
                {attempt.customerMessage}
              </div>
            </div>
          )}

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

          {/* Counterfactual simulation — the alternatives the engine weighed */}
          {counterfactuals && counterfactuals.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 6, letterSpacing: '0.06em' }}>Counterfactual Simulation — What Else Was Considered</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {[...counterfactuals].sort((a, b) => Number(b.incrementalNetValue) - Number(a.incrementalNetValue)).slice(0, 10).map(cf => {
                  const isSel = cf.selected;
                  const inc = Number(cf.incrementalNetValue) || 0;
                  return (
                    <div key={cf.id} style={{ padding: '8px 12px', borderRadius: 'var(--radius-sm)', border: `1px solid ${isSel ? 'var(--gold)' : 'var(--border-subtle)'}`, background: isSel ? 'var(--gold-bg)' : 'var(--bg-secondary)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, fontSize: 12, color: isSel ? 'var(--gold)' : 'var(--text)' }}>
                          {isSel ? '✓ ' : ''}{(cf.action || '').replaceAll('_', ' ')}{cf.discountPercent ? ` · ${cf.discountPercent}%` : ''}
                        </span>
                        <span style={{ display: 'flex', gap: 12, fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
                          <span>p={pct(cf.successProbability)}</span>
                          <span>baseline {pct(cf.baselineProbability)}</span>
                          <span>lift {cf.incrementalLift >= 0 ? '+' : ''}{pct(cf.incrementalLift)}</span>
                          <span style={{ color: inc >= 0 ? 'var(--green)' : 'var(--red)', fontWeight: 700 }}>{inc >= 0 ? '+' : ''}{fmt(cf.incrementalNetValue)}</span>
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Recovery timeline — every attempt on this entity, oldest → newest */}
          {timeline && timeline.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8, letterSpacing: '0.06em' }}>Recovery Timeline</div>
              <div style={{ position: 'relative', paddingLeft: 18 }}>
                {timeline.map((step, i) => (
                  <div key={step.id} style={{ position: 'relative', paddingBottom: i < timeline.length - 1 ? 12 : 0 }}>
                    <div style={{ position: 'absolute', left: -16, top: 4, width: 9, height: 9, borderRadius: '50%', background: step.outcome === 'SUCCESS' ? 'var(--green)' : step.outcome === 'FAILED' ? 'var(--red)' : step.outcome === 'SKIPPED' ? 'var(--text-muted)' : 'var(--gold)', border: '1.5px solid var(--surface)' }} />
                    {i < timeline.length - 1 && <div style={{ position: 'absolute', left: -12, top: 15, width: 1, height: 'calc(100% - 10px)', background: 'var(--border)' }} />}
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text)' }}>
                        <b>{(step.actionTaken || '').replaceAll('_', ' ')}</b>
                        <span style={{ color: 'var(--text-muted)' }}> · {step.outcome}{step.amountRecovered > 0 ? ` · ${fmt(step.amountRecovered)}` : ''}</span>
                      </div>
                      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-muted)' }}>{step.executedAt ? new Date(step.executedAt).toLocaleString() : ''}</div>
                    </div>
                    {step.reasoning && <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.5, marginTop: 2 }}>{step.reasoning}</div>}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
