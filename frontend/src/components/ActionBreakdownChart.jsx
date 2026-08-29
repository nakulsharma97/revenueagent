import { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';
import { fetchActionBreakdown } from '../api';

const ACTION_CONFIG = {
  RETRY_NOW: { color: 'var(--ink-green)', label: 'Retry Now' },
  RETRY_SCHEDULED: { color: '#4CAF50', label: 'Retry Sched.' },
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
      border: '1px solid var(--text)',
      borderRadius: 0,
      padding: '8px 12px',
      fontFamily: 'var(--font-mono)',
      fontSize: 11,
    }}>
      <div style={{ fontWeight: 600, marginBottom: 3, color: 'var(--text)' }}>{d.name}</div>
      <div>Success: {d.successRate}%</div>
      <div>Attempts: {d.total}</div>
      <div>Recovered: ₹{d.recovered.toLocaleString('en-IN')}</div>
      <div>Cost: ₹{d.cost.toLocaleString('en-IN')}</div>
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
      <div className="panel" style={{ padding: '20px 24px', minHeight: 240 }}>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 80 }}>
          LOADING ACTION BREAKDOWN…
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
    <div className="panel" style={{ padding: '20px 24px' }}>
      <div style={{ marginBottom: 16 }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 13,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: 'var(--text)',
          marginBottom: 3,
        }}>
          Success Rate by Action
        </div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
          Which interventions recover money
        </div>
      </div>

      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={chartData} margin={{ left: -10, right: 10, top: 5, bottom: 0 }}>
          <CartesianGrid strokeDasharray="none" stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="name"
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }}
          />
          <YAxis
            stroke="var(--border)"
            tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }}
            domain={[0, 100]}
            tickFormatter={(v) => `${v}%`}
          />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="successRate" radius={0} maxBarSize={40}>
            {chartData.map((d) => {
              const cfg = ACTION_CONFIG[d.action];
              return <Cell key={d.action} fill={cfg?.color || 'var(--text-muted)'} />;
            })}
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        {chartData.map((d) => {
          const cfg = ACTION_CONFIG[d.action];
          return (
            <div key={d.action} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 5,
              padding: '3px 8px',
              border: 'var(--rule)',
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
              color: 'var(--text-secondary)',
            }}>
              <span style={{ width: 6, height: 6, background: cfg?.color || 'var(--text-muted)', flexShrink: 0 }} />
              {d.name}: {d.total}, ₹{d.recovered.toLocaleString('en-IN')}
            </div>
          );
        })}
      </div>
    </div>
  );
}
