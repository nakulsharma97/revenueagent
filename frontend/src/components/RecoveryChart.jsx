import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--text)',
      borderRadius: 0,
      padding: '8px 12px',
      fontFamily: 'var(--font-mono)',
      fontSize: 12,
    }}>
      <div style={{ fontWeight: 600, marginBottom: 3, color: 'var(--text)', fontSize: 11 }}>{d.name}</div>
      <div style={{ color: 'var(--text)', fontWeight: 700 }}>₹{Number(d.value).toLocaleString('en-IN')}</div>
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
    <div className="panel animate-in" style={{ padding: '20px 24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 13,
            fontWeight: 600,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: 'var(--text)',
            marginBottom: 3,
          }}>
            Net Revenue Recovered — Agent vs. Baseline
          </div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
            Recovered minus intervention cost, same batch
          </div>
        </div>

        {improvement > 0 && (
          <div style={{
            padding: '8px 14px',
            background: 'var(--green-bg)',
            border: '1px solid var(--ink-green)',
            textAlign: 'right',
          }}>
            <div style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 18,
              fontWeight: 700,
              color: 'var(--ink-green)',
              lineHeight: 1,
            }}>
              +₹{improvement.toLocaleString('en-IN')}
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--ink-green)', marginTop: 3 }}>
              +{improvementPct}% OVER BASELINE
            </div>
          </div>
        )}
      </div>

      <ResponsiveContainer width="100%" height={100}>
        <BarChart data={data} layout="vertical" margin={{ left: 10, right: 10 }}>
          <CartesianGrid strokeDasharray="none" stroke="var(--border)" horizontal={false} />
          <XAxis
            type="number"
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }}
            tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={180}
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 11, fill: 'var(--text-secondary)' }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="value" radius={0} maxBarSize={28}>
            {data.map((d) => (
              <Cell
                key={d.key}
                fill={d.key === 'agent' ? 'var(--text)' : 'var(--border)'}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
