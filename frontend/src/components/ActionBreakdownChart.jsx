import { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';
import { fetchActionBreakdown } from '../api';

const ACTION_CONFIG = {
  RETRY_NOW: { color: '#059669', label: 'Retry Now' },
  RETRY_SCHEDULED: { color: '#34D399', label: 'Retry Scheduled' },
  SEND_PAYMENT_LINK: { color: '#D97706', label: 'Payment Link' },
  OFFER_DISCOUNT: { color: '#DC2626', label: 'Discount Offer' },
  ESCALATE_TO_HUMAN: { color: '#6366F1', label: 'Escalate' },
  ABANDON: { color: '#6B7280', label: 'Abandon' },
};

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
      <div style={{ color: 'var(--green)' }}>Success rate: {d.successRate}%</div>
      <div style={{ color: 'var(--text-secondary)' }}>Attempts: {d.total}</div>
      <div style={{ color: 'var(--navy)' }}>Recovered: ₹{d.recovered.toLocaleString('en-IN')}</div>
      <div style={{ color: 'var(--text-muted)' }}>Cost: ₹{d.cost.toLocaleString('en-IN')}</div>
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
      <div className="card" style={{ padding: '24px 28px', minHeight: 240 }}>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', paddingTop: 80 }}>
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
    <div className="card" style={{ padding: '24px 28px' }}>
      <div style={{ marginBottom: 20 }}>
        <div style={{
          fontFamily: 'var(--font-display)',
          fontSize: 16,
          fontWeight: 600,
          color: 'var(--text)',
          marginBottom: 4,
        }}>
          Success rate by action
        </div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Which interventions actually recover money
        </div>
      </div>

      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={chartData} margin={{ left: -10, right: 10, top: 5, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="name"
            stroke="var(--text-muted)"
            tick={{ fontSize: 10, fill: 'var(--text-muted)' }}
            axisLine={{ stroke: 'var(--border)' }}
          />
          <YAxis
            stroke="var(--text-muted)"
            tick={{ fontSize: 10, fill: 'var(--text-muted)' }}
            domain={[0, 100]}
            tickFormatter={(v) => `${v}%`}
            axisLine={{ stroke: 'var(--border)' }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Bar dataKey="successRate" radius={[4, 4, 0, 0]} maxBarSize={48}>
            {chartData.map((d) => {
              const cfg = ACTION_CONFIG[d.action];
              return <Cell key={d.action} fill={cfg?.color || '#9CA3AF'} />;
            })}
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      <div style={{
        marginTop: 14,
        display: 'flex',
        flexWrap: 'wrap',
        gap: 8,
      }}>
        {chartData.map((d) => {
          const cfg = ACTION_CONFIG[d.action];
          return (
            <div key={d.action} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '4px 10px',
              background: 'var(--surface-2)',
              borderRadius: 6,
              fontSize: 11,
              color: 'var(--text-secondary)',
              fontFamily: 'var(--font-mono)',
            }}>
              <div style={{
                width: 8,
                height: 8,
                borderRadius: 2,
                background: cfg?.color || '#9CA3AF',
              }} />
              {d.name}: {d.total} tries, ₹{d.recovered.toLocaleString('en-IN')}
            </div>
          );
        })}
      </div>
    </div>
  );
}
