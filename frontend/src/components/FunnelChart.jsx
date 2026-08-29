import { useState, useEffect } from 'react';
import { fetchFunnel } from '../api';

const STAGE_CONFIG = {
  AT_RISK: { color: '#DC2626', label: 'At Risk' },
  IN_RECOVERY: { color: '#D97706', label: 'In Recovery' },
  RECOVERED: { color: '#059669', label: 'Recovered' },
  LOST: { color: '#6B7280', label: 'Lost' },
};

export default function FunnelChart() {
  const [funnel, setFunnel] = useState(null);

  useEffect(() => {
    fetchFunnel().then(setFunnel).catch(() => {});
  }, []);

  if (!funnel) {
    return (
      <div className="card" style={{ padding: '24px 28px', minHeight: 240 }}>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 80 }}>
          Loading funnel data…
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
    <div className="card" style={{ padding: '24px 28px' }}>
      <div style={{ marginBottom: 20 }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 16,
          fontWeight: 600,
          color: 'var(--text)',
          marginBottom: 4,
        }}>
          Recovery pipeline
        </div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Transaction status across the pipeline
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, height: 150, alignItems: 'flex-end' }}>
        {stages.map((s) => {
          const cfg = STAGE_CONFIG[s.key];
          const pct = (s.value / total) * 100;
          const barHeight = Math.max(pct * 1.4, s.value > 0 ? 8 : 2);

          return (
            <div key={s.key} style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 8,
            }}>
              <span style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 14,
                fontWeight: 700,
                color: 'var(--text)',
              }}>
                {s.value}
              </span>

              <div style={{
                width: '100%',
                height: `${barHeight}px`,
                background: cfg.color,
                borderRadius: 4,
                transition: 'height 0.5s ease',
              }} />

              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 11, color: 'var(--text-secondary)', fontWeight: 500 }}>
                  {cfg.label}
                </div>
                <div style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: 10,
                  color: 'var(--text-muted)',
                  marginTop: 2,
                }}>
                  {pct.toFixed(1)}%
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div style={{
        marginTop: 16,
        padding: '10px 14px',
        background: 'var(--surface-2)',
        borderRadius: 8,
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 12,
        color: 'var(--text-muted)',
        fontFamily: 'var(--font-mono)',
      }}>
        <span>{funnel.succeededAttempts} succeeded</span>
        <span style={{ color: 'var(--border)' }}>·</span>
        <span>{funnel.failedAttempts} failed</span>
        <span style={{ color: 'var(--border)' }}>·</span>
        <span>{funnel.pendingAttempts} pending</span>
      </div>
    </div>
  );
}
