import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{ background: 'var(--surface-elevated)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '10px 14px', fontFamily: 'var(--font-body)', fontSize: 12, boxShadow: 'var(--shadow-md)' }}>
      <div style={{ fontWeight: 600, marginBottom: 3, color: 'var(--text-secondary)' }}>{d.name}</div>
      <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--gold-bright)' }}>₹{Number(d.value).toLocaleString('en-IN')}</div>
    </div>
  );
}

export default function RecoveryChart({ netRecovered, baseline }) {
  const data = [
    { name: 'Naive retry-once baseline', value: baseline, key: 'baseline' },
    { name: 'Agent (rules + LLM)', value: netRecovered, key: 'agent' },
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
          <CartesianGrid strokeDasharray="none" stroke="var(--border-subtle)" horizontal={false} />
          <XAxis type="number" stroke="var(--border-subtle)" tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }} tickFormatter={v => `₹${(v / 1000).toFixed(0)}K`} />
          <YAxis type="category" dataKey="name" width={160} stroke="var(--border-subtle)" tick={{ fontFamily: 'var(--font-body)', fontSize: 12, fill: 'var(--text-secondary)' }} />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(201,154,75,0.05)' }} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]} maxBarSize={32}>
            {data.map(d => <Cell key={d.key} fill={d.key === 'agent' ? 'var(--gold)' : 'var(--surface-elevated)'} />)}
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
