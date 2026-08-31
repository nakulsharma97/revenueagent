import { useState, memo } from 'react';

const STAGE_CONFIG = {
  RECOVERED: { color: '#6FAE7B', label: 'Recovered' },
  IN_RECOVERY: { color: '#D6A85A', label: 'In Recovery' },
  AT_RISK: { color: '#D89B32', label: 'At Risk' },
  LOST: { color: '#756C62', label: 'Lost' },
};

function FunnelChartInner({ data: funnel }) {
  const [hovered, setHovered] = useState(null);

  if (!funnel) return (
    <div className="card" style={{ minHeight: 320, width: '100%', minWidth: 0 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>RECOVERY PIPELINE</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Transaction status distribution</div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Waiting for recovery data…</div>
      </div>
    </div>
  );

  const total = funnel.atRisk + funnel.inRecovery + funnel.recovered + funnel.lost || 1;
  const stages = [
    { key: 'RECOVERED', value: funnel.recovered },
    { key: 'IN_RECOVERY', value: funnel.inRecovery },
    { key: 'AT_RISK', value: funnel.atRisk },
    { key: 'LOST', value: funnel.lost },
  ];

  const size = 180, strokeWidth = 28, radius = (size - strokeWidth) / 2, circumference = 2 * Math.PI * radius;
  let accumulatedOffset = 0;
  const segments = stages.map(s => {
    const pct = s.value / total;
    const dashLength = circumference * pct;
    const cfg = STAGE_CONFIG[s.key];
    const seg = {
      ...s, ...cfg, pct,
      dashArray: `${dashLength} ${circumference - dashLength}`,
      dashOffset: -accumulatedOffset,
      pctLabel: ((s.value / total) * 100).toFixed(1),
    };
    accumulatedOffset += dashLength;
    return seg;
  });

  const hoveredSegment = hovered !== null ? segments.find(s => s.key === hovered) : null;

  return (
    <div className="card" style={{ width: '100%', minWidth: 0 }}>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>RECOVERY PIPELINE</div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Transaction status distribution</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 28 }}>
        {/* Donut chart */}
        <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
          <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
            {segments.filter(s => s.value > 0).map(s => (
              <circle
                key={s.key}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={s.color}
                strokeWidth={hovered === s.key ? strokeWidth + 6 : strokeWidth}
                strokeDasharray={s.dashArray}
                strokeDashoffset={s.dashOffset}
                strokeLinecap="butt"
                style={{
                  transition: 'all 0.2s ease',
                  opacity: hovered && hovered !== s.key ? 0.35 : 1,
                  cursor: 'pointer',
                }}
                onMouseEnter={() => setHovered(s.key)}
                onMouseLeave={() => setHovered(null)}
              />
            ))}
          </svg>
          {/* Center: shows total or hovered value */}
          <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
            {hoveredSegment ? (
              <>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 28, fontWeight: 700, color: hoveredSegment.color, lineHeight: 1 }}>{hoveredSegment.value}</div>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>{hoveredSegment.label}</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: hoveredSegment.color, marginTop: 2 }}>{hoveredSegment.pctLabel}%</div>
              </>
            ) : (
              <>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 28, fontWeight: 700, color: 'var(--text)', lineHeight: 1 }}>{total}</div>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>Total</div>
              </>
            )}
          </div>
        </div>

        {/* Legend */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
          {segments.map(s => (
            <div
              key={s.key}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '6px 10px',
                borderRadius: 4,
                background: hovered === s.key ? `${s.color}18` : 'transparent',
                transition: 'background 0.15s ease',
                cursor: 'pointer',
              }}
              onMouseEnter={() => setHovered(s.key)}
              onMouseLeave={() => setHovered(null)}
            >
              <span style={{ width: 10, height: 10, borderRadius: 3, background: s.color, flexShrink: 0 }} />
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, flex: 1 }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 700, color: hovered === s.key ? s.color : 'var(--text)' }}>{s.value}</span>
                <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>{s.label}</span>
              </div>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{s.pctLabel}%</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default memo(FunnelChartInner, (prev, next) => prev.data === next.data);
