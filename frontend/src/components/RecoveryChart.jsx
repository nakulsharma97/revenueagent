import { memo } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

const COLORS = {
  baseline: '#8B7355',
  agent: '#D6A85A',
  baselineBg: 'rgba(139,115,85,0.18)',
  agentBg: 'rgba(214,168,90,0.18)',
};

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{ background: '#1A1511', border: '1px solid #332A22', borderRadius: 4, padding: '10px 14px', fontFamily: 'var(--font-body)', fontSize: 12 }}>
      <div style={{ fontWeight: 600, marginBottom: 3, color: '#A99F93' }}>{d.name}</div>
      <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: '#D6A85A', fontSize: 14 }}>₹{Number(d.displayValue).toLocaleString('en-IN')}</div>
    </div>
  );
}

function RecoveryChartInner({ netRecovered, baseline }) {
  const data = [
    { name: 'Naive retry-once baseline', value: Math.max(baseline, 1000), key: 'baseline', displayValue: baseline },
    { name: 'Agent (rules + LLM)', value: Math.max(netRecovered, 1000), key: 'agent', displayValue: netRecovered },
  ];
  const improvement = netRecovered - baseline;
  const improvementPct = baseline > 0 ? ((improvement / baseline) * 100).toFixed(1) : 0;

  return (
    <div className="card" style={{ width: '100%', minWidth: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>NET REVENUE — AGENT VS. BASELINE</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Recovered minus intervention cost, same batch</div>
        </div>
        {improvement > 0 && (
          <div style={{ textAlign: 'right', flexShrink: 0 }}>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 18, fontWeight: 700, color: 'var(--gold-bright)', lineHeight: 1 }}>+₹{improvement.toLocaleString('en-IN')}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--green)', marginTop: 2 }}>+{improvementPct}% over baseline</div>
          </div>
        )}
      </div>
      <ResponsiveContainer width="100%" height={110}>
        <BarChart data={data} layout="vertical" margin={{ left: 0, right: 60, top: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="none" stroke="rgba(255,255,255,0.06)" horizontal={false} />
          <XAxis type="number" stroke="rgba(255,255,255,0.08)" tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }} tickFormatter={v => `₹${(v / 1000).toFixed(0)}K`} />
          <YAxis type="category" dataKey="name" width={160} stroke="rgba(255,255,255,0.08)" tick={{ fontFamily: 'var(--font-body)', fontSize: 12, fill: 'var(--text-secondary)' }} />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(201,154,75,0.06)' }} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]} maxBarSize={32} barSize={28}>
            {data.map(d => (
              <Cell
                key={d.key}
                fill={d.key === 'agent' ? COLORS.agent : COLORS.baseline}
                fillOpacity={d.key === 'agent' ? 1 : 0.85}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, paddingLeft: 160 }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)' }}>₹{baseline.toLocaleString('en-IN')}</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--gold-bright)', fontWeight: 700 }}>₹{netRecovered.toLocaleString('en-IN')}</span>
      </div>
    </div>
  );
}

export default memo(RecoveryChartInner, (prev, next) => prev.netRecovered === next.netRecovered && prev.baseline === next.baseline);
