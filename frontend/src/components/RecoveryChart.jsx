import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: 8,
      padding: '10px 14px',
      fontFamily: 'var(--font-mono)',
      fontSize: 12,
      boxShadow: 'var(--shadow-md)',
    }}>
      <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text)' }}>{d.name}</div>
      <div style={{ color: 'var(--navy)', fontWeight: 600 }}>₹{Number(d.value).toLocaleString('en-IN')}</div>
    </div>
  );
}

export default function RecoveryChart({ netRecovered, baseline }) {
  const data = [
    { name: 'Naive retry-once baseline', value: baseline, key: 'baseline' },
    { name: 'Agent (bounded rules + LLM)', value: netRecovered, key: 'agent' },
  ];

  const improvement = netRecovered - baseline;
  const improvementPct = baseline > 0 ? ((improvement / baseline) * 100).toFixed(1) : 0;

  return (
    <div className="card animate-in" style={{ padding: '24px 28px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 16,
            fontWeight: 600,
            color: 'var(--text)',
            marginBottom: 4,
          }}>
            Net revenue recovered — agent vs. baseline
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            Recovered minus intervention cost, same batch
          </div>
        </div>

        {improvement > 0 && (
          <div style={{
            padding: '10px 16px',
            background: 'var(--green-bg)',
            border: '1px solid #A7F3D0',
            borderRadius: 8,
            textAlign: 'right',
          }}>
            <div style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 20,
              fontWeight: 700,
              color: 'var(--green)',
              lineHeight: 1,
            }}>
              +₹{improvement.toLocaleString('en-IN')}
            </div>
            <div style={{
              fontSize: 11,
              color: 'var(--green)',
              marginTop: 4,
              fontWeight: 500,
            }}>
              +{improvementPct}% over baseline
            </div>
          </div>
        )}
      </div>

      <ResponsiveContainer width="100%" height={120}>
        <BarChart data={data} layout="vertical" margin={{ left: 10, right: 10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
          <XAxis
            type="number"
            stroke="var(--text-muted)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }}
            tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={180}
            stroke="var(--text-muted)"
            tick={{ fontFamily: 'var(--font-body)', fontSize: 12, fill: 'var(--text-secondary)' }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]} maxBarSize={32}>
            {data.map((d) => (
              <Cell
                key={d.key}
                fill={d.key === 'agent' ? 'var(--navy)' : '#CBD5E1'}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
