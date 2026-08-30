const STAGE_CONFIG = {
  RECOVERED: { color: 'var(--green)', label: 'Recovered' },
  IN_RECOVERY: { color: 'var(--gold)', label: 'In Recovery' },
  AT_RISK: { color: 'var(--amber)', label: 'At Risk' },
  LOST: { color: 'var(--text-muted)', label: 'Lost' },
};

export default function FunnelChart({ data: funnel }) {
  if (!funnel) return (
    <div className="card" style={{ minHeight: 320, width: '100%', minWidth: 0 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>RECOVERY PIPELINE</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Transaction status distribution</div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Loading…</div>
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

  const size = 160, strokeWidth = 24, radius = (size - strokeWidth) / 2, circumference = 2 * Math.PI * radius;
  let accumulatedOffset = 0;
  const segments = stages.map(s => {
    const pct = s.value / total;
    const dashLength = circumference * pct;
    const cfg = STAGE_CONFIG[s.key];
    const seg = { ...s, ...cfg, pct, dashArray: `${dashLength} ${circumference - dashLength}`, dashOffset: -accumulatedOffset };
    accumulatedOffset += dashLength;
    return seg;
  });

  return (
    <div className="card" style={{ width: '100%', minWidth: 0 }}>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>RECOVERY PIPELINE</div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Transaction status distribution</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
          <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
            {segments.filter(s => s.value > 0).map(s => (
              <circle key={s.key} cx={size/2} cy={size/2} r={radius} fill="none" stroke={s.color} strokeWidth={strokeWidth} strokeDasharray={s.dashArray} strokeDashoffset={s.dashOffset} strokeLinecap="round" />
            ))}
          </svg>
          <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 28, fontWeight: 700, color: 'var(--text)', lineHeight: 1 }}>{total}</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>Total</div>
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
          {stages.map(s => {
            const cfg = STAGE_CONFIG[s.key];
            const pct = ((s.value / total) * 100).toFixed(1);
            return (
              <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ width: 10, height: 10, borderRadius: 3, background: cfg.color, flexShrink: 0 }} />
                <div>
                  <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 600, color: 'var(--text)' }}>{s.value}</span>
                  <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginLeft: 6 }}>{cfg.label} ({pct}%)</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
