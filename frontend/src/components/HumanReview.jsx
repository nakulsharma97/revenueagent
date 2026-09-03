import { useEffect, useState } from 'react';
import { fetchReviewQueue, resolveReview, fetchAnomalies } from '../api';

const fmt = (v) => (v === null || v === undefined ? '—' : `₹${Number(v).toLocaleString('en-IN')}`);
const sevColors = { LOW: 'var(--text-muted)', MEDIUM: 'var(--amber)', HIGH: 'var(--gold)', CRITICAL: 'var(--red)' };
const caseColors = { PENDING: 'var(--amber)', APPROVED: 'var(--green)', OVERRIDDEN: 'var(--gold)', REJECTED: 'var(--red)' };
const ACTION_OPTIONS = ['RETRY_SILENT', 'RETRY_NOW', 'RETRY_SCHEDULED', 'SEND_PAYMENT_LINK', 'OFFER_DISCOUNT', 'ESCALATE_TO_HUMAN', 'ABANDON', 'CHECKOUT_REMINDER', 'SEND_REMINDER', 'OFFER_PAYMENT_PLAN', 'PROMISE_FOLLOWUP', 'NO_ACTION'];

export default function HumanReview() {
  const [cases, setCases] = useState([]);
  const [anomalies, setAnomalies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [openAction, setOpenAction] = useState({});   // id -> chosen override action
  const [reason, setReason] = useState({});            // id -> override reason

  async function load() {
    setLoading(true);
    try {
      const [c, a] = await Promise.all([fetchReviewQueue(), fetchAnomalies('OPEN')]);
      setCases(c); setAnomalies(a); setError(null);
    } catch (e) {
      setError('Could not load the review queue — is the backend running on :8080?');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function decide(id, decision) {
    setBusyId(id);
    try {
      const payload = { decision, action: openAction[id] || null, reason: reason[id] || null };
      await resolveReview(id, payload);
      await load();
    } catch (e) {
      setError(e.message || 'Resolution failed');
    } finally {
      setBusyId(null);
    }
  }

  const pending = cases.filter(c => c.status === 'PENDING');
  const openSev = anomalies.filter(a => a.severity === 'HIGH' || a.severity === 'CRITICAL');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
        {[
          { label: 'Awaiting Decision', value: pending.length, color: 'var(--amber)' },
          { label: 'High/Critical Anomalies', value: openSev.length, color: 'var(--red)' },
          { label: 'Total Open Anomalies', value: anomalies.length, color: 'var(--gold)' },
        ].map(s => (
          <div key={s.label} style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '14px 16px' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)', marginBottom: 6 }}>{s.label}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 24, fontWeight: 700, color: s.color }}>{s.value}</div>
          </div>
        ))}
      </div>

      {error && (
        <div className="card" style={{ padding: '18px 20px', color: 'var(--red)' }}>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>⚠ Failed to load review data</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{error}</div>
          <button onClick={load} style={{ marginTop: 10, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '7px 16px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>Retry</button>
        </div>
      )}

      {!error && !loading && pending.length === 0 && (
        <div className="card" style={{ padding: '40px', textAlign: 'center' }}>
          <div style={{ fontSize: 38, opacity: 0.15, marginBottom: 10 }}>✓</div>
          <div style={{ fontFamily: 'var(--font-body)', fontWeight: 600, color: 'var(--text)' }}>Review queue is clear</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>Cases land here when confidence is low, retry budgets are nearly spent, or an anomaly is flagged.</div>
        </div>
      )}

      {pending.map(c => {
        const openOverride = openAction[c.id];
        return (
          <div key={c.id} className="card animate-in" style={{ padding: '18px 20px', borderLeft: `3px solid ${c.priority === 'CRITICAL' ? 'var(--red)' : c.priority === 'HIGH' ? 'var(--gold)' : 'var(--amber)'}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--gold)', fontSize: 13 }}>CASE #{c.id}</span>
                  <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: c.sourceType === 'PAYMENT' ? 'var(--gold-bg)' : c.sourceType === 'CHECKOUT' ? 'var(--amber-bg)' : 'var(--green-bg)', color: c.sourceType === 'PAYMENT' ? 'var(--gold)' : c.sourceType === 'CHECKOUT' ? 'var(--amber)' : 'var(--green)', fontSize: 10, fontWeight: 600 }}>{c.sourceType}</span>
                  {c.priority !== 'NORMAL' && <span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: 'var(--red-bg)', color: 'var(--red)', fontSize: 10, fontWeight: 700 }}>{c.priority}</span>}
                </div>
                <div style={{ marginTop: 8, fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, maxWidth: 720 }}>{c.reason}</div>
                {c.aiRecommendation && (
                  <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)' }}>AI RECOMMENDATION:</span>
                    <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, fontSize: 13, color: 'var(--gold)' }}>
                      {c.aiRecommendation.replaceAll('_', ' ')}{c.aiDiscountPercent ? ` · ${c.aiDiscountPercent}%` : ''}
                    </span>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{(c.aiConfidence * 100).toFixed(0)}% confidence</span>
                  </div>
                )}
              </div>
              <div style={{ textAlign: 'right', flexShrink: 0 }}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 20, fontWeight: 700, color: 'var(--text)' }}>{fmt(c.amount)}</div>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)' }}>ENTITY #{c.sourceEntityId}</div>
              </div>
            </div>

            {openOverride && (
              <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)' }}>Override with:</span>
                <select value={openAction[c.id]} onChange={e => setOpenAction(p => ({ ...p, [c.id]: e.target.value }))}
                  style={{ padding: '6px 10px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: 12, fontFamily: 'var(--font-mono)', color: 'var(--text)', cursor: 'pointer' }}>
                  {ACTION_OPTIONS.map(a => <option key={a} value={a}>{a.replaceAll('_', ' ')}</option>)}
                </select>
              </div>
            )}
            <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button disabled={busyId === c.id} onClick={() => decide(c.id, 'APPROVED')}
                style={{ background: 'var(--green)', color: 'white', border: 'none', borderRadius: 'var(--radius-sm)', padding: '8px 18px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>
                ✓ Approve AI action
              </button>
              <button disabled={busyId === c.id}
                onClick={() => openAction[c.id] ? decide(c.id, 'OVERRIDDEN') : setOpenAction(p => ({ ...p, [c.id]: 'SEND_PAYMENT_LINK' }))}
                style={{ background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '8px 18px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>
                ⇄ {openAction[c.id] ? `Confirm override → ${openAction[c.id].replaceAll('_', ' ')}` : 'Override action'}
              </button>
              <button disabled={busyId === c.id} onClick={() => decide(c.id, 'REJECTED')}
                style={{ background: 'var(--red)', color: 'white', border: 'none', borderRadius: 'var(--radius-sm)', padding: '8px 18px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>
                ✕ Reject
              </button>
              <input placeholder="Reason (optional)" value={reason[c.id] || ''} onChange={e => setReason(p => ({ ...p, [c.id]: e.target.value }))}
                style={{ flex: '1 1 180px', padding: '8px 12px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: 12, color: 'var(--text)', minWidth: 160 }} />
            </div>
          </div>
        );
      })}

      {anomalies.length > 0 && (
        <div className="card" style={{ padding: '18px 20px' }}>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 12 }}>OPEN ANOMALIES</div>
          <div className="table-scroll">
            <table className="main-table">
              <thead><tr>{['ID', 'TYPE', 'SOURCE', 'SEVERITY', 'DESCRIPTION'].map(h => <th key={h}>{h}</th>)}</tr></thead>
              <tbody>{anomalies.map(a => (
                <tr key={a.id}>
                  <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 600 }}>#{a.id}</td>
                  <td style={{ fontWeight: 600 }}>{a.type.replaceAll('_', ' ')}</td>
                  <td style={{ color: 'var(--text-muted)' }}>{a.sourceType} #{a.sourceEntityId}</td>
                  <td><span style={{ color: sevColors[a.severity] || 'var(--text-muted)', fontWeight: 700, fontSize: 12 }}>{a.severity}</span></td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{a.description}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </div>
      )}

      {cases.filter(c => c.status !== 'PENDING').length > 0 && (
        <div className="card" style={{ padding: '18px 20px' }}>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 12 }}>RESOLVED CASES</div>
          <div className="table-scroll">
            <table className="main-table">
              <thead><tr>{['CASE', 'SOURCE', 'AI RECOMMENDATION', 'STATUS', 'HUMAN DECISION', 'REASON'].map(h => <th key={h}>{h}</th>)}</tr></thead>
              <tbody>{cases.filter(c => c.status !== 'PENDING').slice(0, 30).map(c => (
                <tr key={c.id}>
                  <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 600 }}>#{c.id}</td>
                  <td style={{ color: 'var(--text-muted)' }}>{c.sourceType} #{c.sourceEntityId}</td>
                  <td style={{ textTransform: 'capitalize' }}>{c.aiRecommendation?.replaceAll('_', ' ').toLowerCase()}</td>
                  <td><span style={{ color: caseColors[c.status] || 'var(--text)', fontWeight: 700, fontSize: 11 }}>{c.status}</span></td>
                  <td style={{ textTransform: 'capitalize' }}>{c.humanDecision ? c.humanDecision.replaceAll('_', ' ').toLowerCase() : '—'}</td>
                  <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>{c.overrideReason || '—'}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
