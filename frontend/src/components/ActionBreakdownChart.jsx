import { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';
import { fetchActionBreakdown } from '../api';

const ACTION_CONFIG = {
  RETRY_NOW: { color: 'var(--ink-green)', label: 'Retry Now' },
  RETRY_SCHEDULED: { color: 'var(--ink-blue)', label: 'Retry Sched.' },
  SEND_PAYMENT_LINK: { color: 'var(--ink-amber)', label: 'Pay Link' },
  OFFER_DISCOUNT: { color: 'var(--ink-red)', label: 'Discount' },
  ESCALATE_TO_HUMAN: { color: 'var(--ink-purple)', label: 'Escalate' },
  ABANDON: { color: 'var(--text-muted)', label: 'Abandon' },
};

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
      <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text)' }}>{d.name}</div>
      <div>Success: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{d.successRate}%</span></div>
      <div>Attempts: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{d.total}</span></div>
      <div>Recovered: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>₹{d.recovered.toLocaleString('en-IN')}</span></div>
      <div>Cost: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>₹{d.cost.toLocaleString('en-IN')}</span></div>
    </div>
  );
}

export default function ActionBreakdownChart() {
  const [data, setData] = useState([]);

  useEffect(() => {
    fetchActionBreakdown().then(setData).catch(() => {});
  }, []);

  if (data.length === 0) {
    return (
      <div className="card" style={{ minHeight: 320 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 120 }}>
          Loading action breakdown…
        </div>
      </div>
    );
  }

  const chartData = data.map((d) => ({
    name: ACTION_CONFIG[d.action]?.label || d.action,
    action: d.action,
    successRate: d.successRate,
    total: d.totalAttempts,
    recovered: Number(d.amountRecovered),
    cost: Number(d.interventionCost),
  }));

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
          SUCCESS RATE BY ACTION
        </div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>
          Which interventions recover money
        </div>
      </div>

      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={chartData} margin={{ left: -10, right: 10, top: 5, bottom: 0 }}>
          <CartesianGrid strokeDasharray="none" stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="name"
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-body)', fontSize: 11, fill: 'var(--text-muted)' }}
          />
          <YAxis
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }}
            domain={[0, 100]}
            tickFormatter={(v) => `${v}%`}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(0,0,0,0.03)' }} />
          <Bar dataKey="successRate" radius={[4, 4, 0, 0]} maxBarSize={40}>
            {chartData.map((d) => {
              const cfg = ACTION_CONFIG[d.action];
              return <Cell key={d.action} fill={cfg?.color || 'var(--text-muted)'} />;
            })}
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      {/* Legend */}
      <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {chartData.map((d) => {
          const cfg = ACTION_CONFIG[d.action];
          return (
            <div key={d.action} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '4px 10px',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-full)',
              fontSize: 11,
              fontFamily: 'var(--font-body)',
              color: 'var(--text-secondary)',
            }}>
              <span style={{
                width: 8,
                height: 8,
                borderRadius: 2,
                background: cfg?.color || 'var(--text-muted)',
                flexShrink: 0,
              }} />
              {d.name}: {d.total} (₹{d.recovered.toLocaleString('en-IN')})
            </div>
          );
        })}
      </div>
    </div>
  );
}
