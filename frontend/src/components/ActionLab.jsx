import { useEffect, useState } from 'react';
import { fetchActionPerformance, fetchExperiments, createExperiment } from '../api';

const fmt = (v) => (v === null || v === undefined ? '—' : `₹${Number(v).toLocaleString('en-IN')}`);

export default function ActionLab() {
  const [rows, setRows] = useState([]);
  const [experiments, setExperiments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [draft, setDraft] = useState({ name: '', description: '', controlPercentage: 15, treatmentPolicy: '', targetSegment: 'ALL', endDate: '' });
  const [creating, setCreating] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [r, e] = await Promise.all([fetchActionPerformance(), fetchExperiments()]);
      setRows(r); setExperiments(e); setError(null);
    } catch (err) {
      setError('Could not load the Action Performance Lab — is the backend running on :8080?');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function create() {
    if (!draft.name.trim()) return;
    setCreating(true);
    try {
      await createExperiment({ ...draft, controlPercentage: Number(draft.controlPercentage) });
      setDraft({ name: '', description: '', controlPercentage: 15, treatmentPolicy: '', targetSegment: 'ALL', endDate: '' });
      await load();
    } catch (err) {
      setError(err.message || 'Could not create experiment');
    } finally {
      setCreating(false);
    }
  }

  const inputStyle = { width: '100%', padding: '8px 12px', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text)', background: 'var(--bg-secondary)' };
  const labelStyle = { display: 'block', fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 5 };

  const maxNet = rows.length ? Math.max(1, ...rows.map(r => Number(r.netValue) || 0)) : 1;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6 }}>
        Which recovery actions actually <b>work</b>? The lab ranks every executed action by
        <b> net value</b> (recovered − intervention cost), not by success rate — a reminder that
        recovers ₹8,000 at low cost beats a discount that recovers ₹9,000 after giving away ₹2,000.
        Outcomes are recorded automatically by the Outcome Learning loop after every batch.
      </div>

      {error && (
        <div className="card" style={{ padding: '16px 18px', color: 'var(--red)' }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>⚠ {error}</div>
          <button onClick={load} style={{ marginTop: 8, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '7px 16px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>Retry</button>
        </div>
      )}

      <div className="card" style={{ padding: '18px 20px' }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 12 }}>ACTION PERFORMANCE — RANKED BY NET VALUE</div>
        {loading ? <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>Loading outcomes…</div> : rows.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13, padding: '20px 0', textAlign: 'center' }}>No outcomes recorded yet — run a recovery batch and the learning loop will populate this table.</div>
        ) : (
          <div className="table-scroll">
            <table className="main-table">
              <thead>
                <tr>
                  {['ACTION', 'ATTEMPTS', 'SUCCESS RATE', 'RECOVERED', 'COST', 'NET VALUE', 'NET VALUE SHARE', 'AVG FATIGUE AT DECISION'].map(h => (
                    <th key={h} style={{ whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map(r => {
                  const share = (Number(r.netValue) / maxNet) * 100;
                  return (
                    <tr key={r.action}>
                      <td style={{ fontWeight: 700, color: 'var(--text)', textTransform: 'capitalize', whiteSpace: 'nowrap' }}>{r.action.replaceAll('_', ' ').toLowerCase()}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-secondary)' }}>{r.attempts}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: r.successRate >= 50 ? 'var(--green)' : 'var(--text-secondary)', fontWeight: 600 }}>{r.successRate}%</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--green)', fontWeight: 600 }}>{fmt(r.recovered)}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-muted)' }}>{Number(r.interventionCost) > 0 ? fmt(r.interventionCost) : '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', fontWeight: 800, color: Number(r.netValue) >= 0 ? 'var(--gold-bright)' : 'var(--red)' }}>{fmt(r.netValue)}</td>
                      <td style={{ minWidth: 160 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <div style={{ flex: 1, height: 6, background: 'var(--border-subtle)', borderRadius: 3, overflow: 'hidden' }}>
                            <div style={{ width: `${share}%`, height: '100%', background: Number(r.netValue) >= 0 ? 'var(--gold)' : 'var(--red)', borderRadius: 3 }} />
                          </div>
                          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-muted)', width: 40, textAlign: 'right' }}>{share.toFixed(0)}%</span>
                        </div>
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-muted)' }}>{r.avgFatigueAtDecision ? `${(r.avgFatigueAtDecision * 100).toFixed(0)}%` : '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Experiments */}
      <div className="card" style={{ padding: '18px 20px' }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', marginBottom: 4 }}>EXPERIMENTATION MODE</div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>
          Declared policies only — control/treatment assignment stays in the ingestion layer, so experiment logic never mixes into per-entity decisions.
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 10, marginBottom: 16 }}>
          {experiments.map(e => (
            <div key={e.id} style={{ padding: '12px 14px', background: 'var(--bg-secondary)', border: '1px solid', borderColor: e.status === 'ACTIVE' ? 'var(--green)' : 'var(--border)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <span style={{ fontFamily: 'var(--font-body)', fontWeight: 700, fontSize: 13, color: 'var(--text)' }}>{e.name}</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9, padding: '2px 6px', borderRadius: 'var(--radius-full)', color: e.status === 'ACTIVE' ? 'var(--green)' : 'var(--text-muted)', background: e.status === 'ACTIVE' ? 'var(--green-bg)' : 'var(--surface-hover)', fontWeight: 700 }}>{e.status}</span>
              </div>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.5, marginTop: 6 }}>{e.description}</div>
              <div style={{ marginTop: 8, display: 'flex', gap: 10, flexWrap: 'wrap', fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-secondary)' }}>
                <span>control {e.controlPercentage}%</span>
                <span>{e.targetCustomerSegment === 'ALL' ? 'all segments' : e.targetCustomerSegment}</span>
                {e.endDate && <span>until {e.endDate}</span>}
              </div>
            </div>
          ))}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12, borderTop: '1px solid var(--border-subtle)', paddingTop: 14 }}>
          {[
            { key: 'name', label: 'Experiment Name', type: 'text', placeholder: 'e.g. Pay-link vs Reminder (Phase 2)' },
            { key: 'treatmentPolicy', label: 'Treatment Policy', type: 'text', placeholder: 'SEND_PAYMENT_LINK vs SEND_REMINDER' },
            { key: 'controlPercentage', label: 'Control %', type: 'number' },
            { key: 'targetSegment', label: 'Target Source', type: 'select' },
            { key: 'endDate', label: 'End Date (yyyy-mm-dd)', type: 'text' },
          ].map(f => (
            <div key={f.key}>
              <label style={labelStyle}>{f.label}</label>
              {f.type === 'select' ? (
                <select style={inputStyle} value={draft.targetSegment} onChange={e => setDraft(p => ({ ...p, targetSegment: e.target.value }))}>
                  {['ALL', 'PAYMENT', 'CHECKOUT', 'RECEIVABLE'].map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              ) : (
                <input style={inputStyle} type={f.type} placeholder={f.placeholder || ''} value={draft[f.key]}
                  onChange={e => setDraft(p => ({ ...p, [f.key]: f.type === 'number' ? Number(e.target.value) : e.target.value }))} />
              )}
            </div>
          ))}
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button onClick={create} disabled={creating || !draft.name.trim()} style={{ width: '100%', background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '9px 16px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 12, cursor: creating ? 'not-allowed' : 'pointer' }}>
              {creating ? 'Creating…' : '+ Declare Experiment'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
