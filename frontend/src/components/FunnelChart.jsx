import { useState, useEffect } from 'react';
import { fetchFunnel } from '../api';

const STAGE_CONFIG = {
  RECOVERED: { color: 'var(--ink-green)', label: 'Recovered' },
  IN_RECOVERY: { color: 'var(--ink-amber)', label: 'In Recovery' },
  AT_RISK: { color: 'var(--ink-red)', label: 'At Risk' },
  LOST: { color: 'var(--text-muted)', label: 'Lost' },
};

export default function FunnelChart() {
  const [funnel, setFunnel] = useState(null);

  useEffect(() => {
    fetchFunnel().then(setFunnel).catch(() => {});
  }, []);

  if (!funnel) {
    return (
      <div className="card" style={{ minHeight: 320 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 120 }}>
          Loading funnel…
        </div>
      </div>
    );
  }

  const total = funnel.atRisk + funnel.inRecovery + funnel.recovered + funnel.lost || 1;
  const stages = [
    { key: 'RECOVERED', value: funnel.recovered },
    { key: 'IN_RECOVERY', value: funnel.inRecovery },
    { key: 'AT_RISK', value: funnel.atRisk },
    { key: 'LOST', value: funnel.lost },
  ];

  // Build SVG donut
  const size = 160;
  const strokeWidth = 24;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  let accumulatedOffset = 0;

  const segments = stages.map((s) => {
    const pct = s.value / total;
    const dashLength = circumference * pct;
    const gap = circumference - dashLength;
    const cfg = STAGE_CONFIG[s.key];
    const seg = {
      ...s,
      ...cfg,
      pct,
      dashArray: `${dashLength} ${gap}`,
      dashOffset: -accumulatedOffset,
    };
    accumulatedOffset += dashLength;
    return seg;
  });

  return (
    <div className="card">
      <div style={{ marginBottom: 16 }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 15,
          fontWeight: 700,
          color: 'var(--text)',
          marginBottom: 2,
        }}>
          RECOVERY PIPELINE
        </div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>
          Transaction status distribution
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        {/* Donut */}
        <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
          <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
            {segments.filter(s => s.value > 0).map((s) => (
              <circle
                key={s.key}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={s.color}
                strokeWidth={strokeWidth}
                strokeDasharray={s.dashArray}
                strokeDashoffset={s.dashOffset}
                strokeLinecap="round"
              />
            ))}
          </svg>
          {/* Center text */}
          <div style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontSize: 28,
              fontWeight: 700,
              color: 'var(--text)',
              lineHeight: 1,
            }}>
              {total}
            </div>
            <div style={{
              fontFamily: 'var(--font-body)',
              fontSize: 11,
              color: 'var(--text-muted)',
              marginTop: 2,
            }}>
              Total
            </div>
          </div>
        </div>

        {/* Legend */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {stages.map((s) => {
            const cfg = STAGE_CONFIG[s.key];
            const pct = ((s.value / total) * 100).toFixed(1);
            return (
              <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{
                  width: 10,
                  height: 10,
                  borderRadius: 3,
                  background: cfg.color,
                  flexShrink: 0,
                }} />
                <div>
                  <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 600, color: 'var(--text)' }}>
                    {s.value}
                  </span>
                  <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginLeft: 6 }}>
                    {cfg.label} ({pct}%)
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
