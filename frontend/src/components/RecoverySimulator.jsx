import { useState } from 'react';
import { simulateRecovery } from '../api';

const FAILURE_REASONS = [
  'NETWORK_ERROR', 'BANK_SERVER_DOWN', 'INSUFFICIENT_FUNDS', 'CARD_EXPIRED', 'INVALID_CVV',
  'UPI_PIN_MISMATCH', 'UPI_TIMEOUT', 'VPA_INVALID', 'BANK_SESSION_EXPIRED', 'CARD_STOLEN_FLAG',
];
const ABANDON_REASONS = ['PRICE_HESITATION', 'PAYMENT_METHOD_DECLINED', 'DISTRACTED_NO_COMPLETION', 'TECHNICAL_ERROR'];

const fmt = (v) => (v === null || v === undefined ? '—' : `₹${Number(v).toLocaleString('en-IN')}`);
const pct = (v) => (v === null || v === undefined ? '—' : `${(v * 100).toFixed(0)}%`);

const inputStyle = {
  width: '100%', padding: '9px 12px', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)',
  fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--text)', background: 'var(--bg-secondary)',
};
const labelStyle = {
  display: 'block', fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600,
  textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 6,
};

export default function RecoverySimulator() {
  const [form, setForm] = useState({
    sourceType: 'PAYMENT', amount: 20000, failureReason: 'INSUFFICIENT_FUNDS',
    retryCount: 0, reminderCount: 0, daysOverdue: 20, promiseBroken: false,
    reliability: 0.6, highValue: false, language: 'en',
  });
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }));

  async function run() {
    setLoading(true); setError(null); setResult(null);
    try {
      setResult(await simulateRecovery(form));
    } catch (e) {
      setError(e.message || 'Simulation failed — is the backend running?');
    } finally {
      setLoading(false);
    }
  }

  const isCheckout = form.sourceType === 'CHECKOUT';
  const isReceivable = form.sourceType === 'RECEIVABLE';
  const reasons = isCheckout ? ABANDON_REASONS : FAILURE_REASONS;

  const chosen = result?.chosen;
  const stateColors = { STOP_INTERVENTION: 'var(--red)', RECOVERY_FATIGUE: 'var(--amber)', HUMAN_ATTENTION_REQUIRED: 'var(--red)', HIGH_VALUE_AT_RISK: 'var(--gold)', LIKELY_TO_SELF_RECOVER: 'var(--green)', NEW_FAILURE: 'var(--text-secondary)' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6 }}>
        Build a hypothetical customer case and watch the engine simulate every allowed action,
        score it by <b>expected incremental net value</b>, and recommend the next best action —
        never by raw success probability alone.
      </div>

      <div className="card" style={{ padding: 20 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 16 }}>
          <div>
            <label style={labelStyle}>Revenue Source</label>
            <select style={inputStyle} value={form.sourceType} onChange={e => { set('sourceType', e.target.value); set('failureReason', e.target.value === 'CHECKOUT' ? 'DISTRACTED_NO_COMPLETION' : 'INSUFFICIENT_FUNDS'); }}>
              <option value="PAYMENT">Payment Failure</option>
              <option value="CHECKOUT">Checkout Abandonment</option>
              <option value="RECEIVABLE">B2B Receivable</option>
            </select>
          </div>
          <div>
            <label style={labelStyle}>Amount at Risk (₹)</label>
            <input style={inputStyle} type="number" min={1} value={form.amount} onChange={e => set('amount', Number(e.target.value))} />
          </div>
          <div>
            <label style={labelStyle}>{isCheckout ? 'Abandonment Reason' : isReceivable ? 'Days Overdue' : 'Failure Reason'}</label>
            {isReceivable
              ? <input style={inputStyle} type="number" min={0} value={form.daysOverdue} onChange={e => set('daysOverdue', Number(e.target.value))} />
              : <select style={inputStyle} value={form.failureReason} onChange={e => set('failureReason', e.target.value)}>
                {reasons.map(r => <option key={r} value={r}>{r.replaceAll('_', ' ')}</option>)}
              </select>}
          </div>
          {!isReceivable && (
            <div>
              <label style={labelStyle}>{isCheckout ? 'Reminders Sent' : 'Retries So Far'}</label>
              <input style={inputStyle} type="number" min={0} max={6} value={isCheckout ? form.reminderCount : form.retryCount}
                onChange={e => isCheckout ? set('reminderCount', Number(e.target.value)) : set('retryCount', Number(e.target.value))} />
            </div>
          )}
          {!isCheckout && (
            <div>
              <label style={labelStyle}>Customer Reliability (0–1)</label>
              <input style={inputStyle} type="number" min={0} max={1} step={0.05} value={form.reliability}
                onChange={e => set('reliability', Number(e.target.value))} />
            </div>
          )}
          {isReceivable && (
            <div>
              <label style={labelStyle}>Broken Promise-to-Pay</label>
              <select style={inputStyle} value={String(form.promiseBroken)} onChange={e => set('promiseBroken', e.target.value === 'true')}>
                <option value="false">No</option><option value="true">Yes</option>
              </select>
            </div>
          )}
          {!isReceivable && !isCheckout && (
            <div>
              <label style={labelStyle}>Customer Segment</label>
              <select style={inputStyle} value={String(form.highValue)} onChange={e => { set('highValue', e.target.value === 'true'); }}>
                <option value="false">STANDARD (3 retries · 15% cap)</option>
                <option value="true">HIGH_VALUE (5 retries · 25% cap)</option>
              </select>
            </div>
          )}
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button onClick={run} disabled={loading}
              style={{ width: '100%', background: loading ? 'var(--text-muted)' : 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '10px 16px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: loading ? 'not-allowed' : 'pointer' }}>
              {loading ? 'Simulating…' : '◈ SIMULATE RECOVERY'}
            </button>
          </div>
        </div>
        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: 'var(--red-bg)', border: '1px solid var(--red-border)', color: 'var(--red)', borderRadius: 'var(--radius-sm)', fontSize: 12 }}>{error}</div>}
      </div>

      {result && (
        <div className="animate-in" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {/* Recommendation banner */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
            <div style={{ gridColumn: 'span 2', background: 'linear-gradient(135deg, var(--gold-bg), transparent)', border: '1px solid var(--gold-border)', borderRadius: 'var(--radius-sm)', padding: '18px 20px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--gold)', marginBottom: 6 }}>AI Recommendation · Next Best Action</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 26, fontWeight: 800, color: 'var(--text)', letterSpacing: '0.02em' }}>
                {chosen?.displayName || result.reasoning || 'NO ACTION'}
              </div>
              <div style={{ marginTop: 6, fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>{result.reasoning}</div>
            </div>
            <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '14px 16px' }}>
              <div style={labelStyle}>Decision Confidence</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 26, fontWeight: 700, color: result.automationPolicy === 'HUMAN_REVIEW' ? 'var(--red)' : result.automationPolicy === 'SAFE_ACTION_ONLY' ? 'var(--amber)' : 'var(--green)' }}>
                {pct(result.confidence)}
              </div>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600, marginTop: 4 }}>{result.automationPolicy?.replaceAll('_', ' ')}</div>
            </div>
            <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '14px 16px' }}>
              <div style={labelStyle}>Customer State</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 15, fontWeight: 700, color: stateColors[result.recoveryState] || 'var(--text)' }}>{result.recoveryState?.replaceAll('_', ' ')}</div>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)', marginTop: 4 }}>Fatigue {pct(result.fatigueScore)} · {result.fatigueBand?.toLowerCase()}</div>
            </div>
            <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '14px 16px' }}>
              <div style={labelStyle}>Natural Baseline</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 20, fontWeight: 700, color: 'var(--text-secondary)' }}>{pct(result.baselineProbability)}</div>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)', marginTop: 4 }}>would pay anyway</div>
            </div>
          </div>

          {/* Counterfactual ranking */}
          <div className="card" style={{ padding: '18px 20px' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text)', marginBottom: 4 }}>COUNTERFACTUAL SIMULATION — {result.alternatives?.length || 0} ACTIONS EVALUATED</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>Every allowed action was simulated against the {pct(result.baselineProbability)} natural-recovery baseline. Ranked by expected incremental net value.</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {(result.alternatives || []).map(alt => {
                const isChosen = chosen && alt.action === chosen.action && (alt.discountPercent || null) === (chosen.discountPercent || null);
                const maxInc = Math.max(0.01, ...(result.alternatives || []).map(a => Number(a.incrementalNetValue) || 0));
                const inc = Number(alt.incrementalNetValue) || 0;
                return (
                  <div key={`${alt.action}-${alt.discountPercent || 0}`} style={{
                    padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: `1px solid ${isChosen ? 'var(--gold)' : 'var(--border)'}`,
                    background: isChosen ? 'var(--gold-bg)' : 'var(--surface)',
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 200 }}>
                        {isChosen && <span style={{ color: 'var(--gold)', fontSize: 12 }}>✓</span>}
                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, fontSize: 13, color: isChosen ? 'var(--gold)' : 'var(--text)' }}>{alt.displayName}</span>
                        {isChosen && <span style={{ fontFamily: 'var(--font-body)', fontSize: 9, fontWeight: 700, letterSpacing: '0.05em', background: 'var(--gold)', color: 'var(--text-inverse)', borderRadius: 'var(--radius-full)', padding: '2px 8px' }}>SELECTED</span>}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 14, fontFamily: 'var(--font-mono)', fontSize: 11, flexWrap: 'wrap' }}>
                        <span style={{ color: 'var(--text-muted)' }}>success {pct(alt.successProbability)}</span>
                        <span style={{ color: inc >= 0 ? 'var(--green)' : 'var(--red)', fontWeight: 700, fontSize: 12 }}>{inc >= 0 ? '+' : ''}{fmt(alt.incrementalNetValue)}</span>
                        <span style={{ color: 'var(--text-muted)' }}>{fmt(alt.expectedNetValue)} net</span>
                      </div>
                    </div>
                    <div style={{ marginTop: 8, height: 4, background: 'var(--border-subtle)', borderRadius: 2, overflow: 'hidden' }}>
                      <div style={{ width: `${Math.max(2, (inc / maxInc) * 100)}%`, height: '100%', background: isChosen ? 'var(--gold)' : inc >= 0 ? 'var(--green)' : 'var(--red)', borderRadius: 2 }} />
                    </div>
                    <div style={{ marginTop: 6, fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)' }}>{alt.reasoning}</div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Why */}
          <div className="card" style={{ padding: '16px 20px' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 10 }}>DECISION EXPLAINABILITY — TOP FACTORS</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {(result.topFactors || []).map((f, i) => (
                <div key={i} style={{ display: 'flex', gap: 10, fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-secondary)' }}>
                  <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 700, fontSize: 11, width: 18, flexShrink: 0 }}>{i + 1}.</span>
                  <span>{f}</span>
                </div>
              ))}
            </div>
            {result.customerMessage && (
              <div style={{ marginTop: 12, padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', fontStyle: 'italic', fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-secondary)' }}>
                Draft message: “{result.customerMessage}”
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
