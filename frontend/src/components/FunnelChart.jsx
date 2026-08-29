import { useState, useEffect } from 'react';
import { fetchFunnel } from '../api';

const STAGE_CONFIG = {
  AT_RISK: { color: 'var(--ink-red)', label: 'At Risk' },
  IN_RECOVERY: { color: 'var(--ink-amber)', label: 'In Recovery' },
  RECOVERED: { color: 'var(--ink-green)', label: 'Recovered' },
  LOST: { color: 'var(--text-muted)', label: 'Lost' },
};

export default function FunnelChart() {
  const [funnel, setFunnel] = useState(null);

  useEffect(() => {
    fetchFunnel().then(setFunnel).catch(() => {});
  }, []);

  if (!funnel) {
    return (
      <div className="panel" style={{ padding: '20px 24px', minHeight: 240 }}>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 80 }}>
          LOADING FUNNEL…
        </div>
      </div>
    );
  }

  const total = funnel.atRisk + funnel.inRecovery + funnel.recovered + funnel.lost || 1;
  const stages = [
    { key: 'AT_RISK', value: funnel.atRisk },
    { key: 'IN_RECOVERY', value: funnel.inRecovery },
    { key: 'RECOVERED', value: funnel.recovered },
    { key: 'LOST', value: funnel.lost },
  ];

  return (
    <div className="panel" style={{ padding: '20px 24px' }}>
      <div style={{ marginBottom: 16 }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 13,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: 'var(--text)',
          marginBottom: 3,
        }}>
          Recovery Pipeline
        </div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
          Transaction status distribution
        </div>
      </div>

      <div style={{ display: 'flex', gap: 6, height: 140, alignItems: 'flex-end' }}>
        {stages.map((s) => {
          const cfg = STAGE_CONFIG[s.key];
          const pct = (s.value / total) * 100;
          const barHeight = Math.max(pct * 1.4, s.value > 0 ? 6 : 2);

          return (
            <div key={s.key} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 700, color: 'var(--text)' }}>
                {s.value}
              </span>
              <div style={{
                width: '100%',
                height: `${barHeight}px`,
                background: cfg.color,
                borderRadius: 0,
                transition: 'height 0.3s ease',
              }} />
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-secondary)', fontWeight: 500 }}>
                  {cfg.label}
                </div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 9, color: 'var(--text-muted)', marginTop: 1 }}>
                  {pct.toFixed(1)}%
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div style={{
        marginTop: 12,
        padding: '8px 10px',
        background: 'var(--surface-2)',
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 11,
        fontFamily: 'var(--font-mono)',
        color: 'var(--text-muted)',
      }}>
        <span>{funnel.succeededAttempts} succeeded</span>
        <span>│</span>
        <span>{funnel.failedAttempts} failed</span>
        <span>│</span>
        <span>{funnel.pendingAttempts} skipped</span>
      </div>
    </div>
  );
}
