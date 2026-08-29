import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius-sm)',
      boxShadow: 'var(--shadow-md)',
      padding: '10px 14px',
      fontFamily: 'var(--font-body)',
      fontSize: 12,
    }}>
      <div style={{ fontWeight: 600, marginBottom: 3, color: 'var(--text)' }}>{d.name}</div>
      <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text)' }}>
        ₹{Number(d.value).toLocaleString('en-IN')}
      </div>
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
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 15,
            fontWeight: 700,
            color: 'var(--text)',
            marginBottom: 2,
          }}>
            NET REVENUE RECOVERED — AGENT VS. BASELINE
          </div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>
            Recovered minus intervention cost, same batch
          </div>
        </div>
        {improvement > 0 && (
          <div style={{ textAlign: 'right' }}>
            <div style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 18,
              fontWeight: 700,
              color: 'var(--green)',
              lineHeight: 1,
            }}>
              +₹{improvement.toLocaleString('en-IN')}
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--green)', marginTop: 2 }}>
              +{improvementPct}% over baseline
            </div>
          </div>
        )}
      </div>

      <ResponsiveContainer width="100%" height={110}>
        <BarChart data={data} layout="vertical" margin={{ left: 0, right: 50, top: 0, bottom: 0 }}>
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
            width={150}
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-body)', fontSize: 12, fill: 'var(--text-secondary)' }}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(0,0,0,0.03)' }} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]} maxBarSize={32}>
            {data.map((d) => (
              <Cell
                key={d.key}
                fill={d.key === 'agent' ? 'var(--green)' : 'var(--surface-3)'}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      {/* Value labels */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, paddingLeft: 150 }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)', fontWeight: 500 }}>
          ₹{baseline.toLocaleString('en-IN')}
        </span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--green)', fontWeight: 700 }}>
          ₹{netRecovered.toLocaleString('en-IN')}
        </span>
      </div>
    </div>
  );
}
