import { memo } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts';

const ACTION_CONFIG = {
  RETRY_NOW: { color: 'var(--gold)', label: 'Retry Now' },
  RETRY_SCHEDULED: { color: 'var(--gold-soft)', label: 'Retry Sched.' },
  SEND_PAYMENT_LINK: { color: 'var(--amber)', label: 'Pay Link' },
  OFFER_DISCOUNT: { color: 'var(--gold-bright)', label: 'Discount' },
  ESCALATE_TO_HUMAN: { color: 'var(--red)', label: 'Escalate' },
  ABANDON: { color: 'var(--text-muted)', label: 'Abandon' },
  CHECKOUT_REMINDER: { color: 'var(--amber)', label: 'Cart Rmndr' },
  OFFER_PAYMENT_PLAN: { color: 'var(--green)', label: 'Payment Plan' },
  SEND_REMINDER: { color: 'var(--gold)', label: 'Reminder' },
};

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div style={{ background: 'var(--surface-elevated)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '10px 14px', fontFamily: 'var(--font-body)', fontSize: 12, boxShadow: 'var(--shadow-md)' }}>
      <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text)' }}>{d.name}</div>
      <div>Success: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--gold-bright)' }}>{d.successRate}%</span></div>
      <div>Attempts: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{d.total}</span></div>
      <div>Recovered: <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--green)' }}>₹{d.recovered.toLocaleString('en-IN')}</span></div>
    </div>
  );
}

function ActionBreakdownChartInner({ data = [] }) {
  if (data.length === 0) return (
    <div className="card" style={{ minHeight: 320, width: '100%', minWidth: 0 }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>SUCCESS RATE BY ACTION</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Which interventions recover money</div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Waiting for recovery data…</div>
      </div>
    </div>
  );

  const chartData = data.map(d => ({
    name: ACTION_CONFIG[d.action]?.label || d.action,
    action: d.action,
    successRate: d.successRate,
    total: d.totalAttempts,
    recovered: Number(d.amountRecovered),
    cost: Number(d.interventionCost),
  }));

  return (
    <div className="card" style={{ width: '100%', minWidth: 0 }}>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>SUCCESS RATE BY ACTION</div>
        <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Which interventions recover money</div>
      </div>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={chartData} margin={{ left: -10, right: 10, top: 5, bottom: 0 }}>
          <CartesianGrid strokeDasharray="none" stroke="var(--border-subtle)" vertical={false} />
          <XAxis dataKey="name" stroke="var(--border-subtle)" tick={{ fontFamily: 'var(--font-body)', fontSize: 10, fill: 'var(--text-muted)' }} />
          <YAxis stroke="var(--border-subtle)" tick={{ fontFamily: 'var(--font-mono)', fontSize: 10, fill: 'var(--text-muted)' }} domain={[0, 100]} tickFormatter={v => `${v}%`} />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(201,154,75,0.05)' }} />
          <Bar dataKey="successRate" radius={[4, 4, 0, 0]} maxBarSize={40}>
            {chartData.map(d => <Cell key={d.action} fill={ACTION_CONFIG[d.action]?.color || 'var(--text-muted)'} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {chartData.map(d => {
          const cfg = ACTION_CONFIG[d.action];
          return (
            <div key={d.action} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px', border: '1px solid var(--border)', borderRadius: 'var(--radius-full)', fontSize: 11, fontFamily: 'var(--font-body)', color: 'var(--text-secondary)' }}>
              <span style={{ width: 8, height: 8, borderRadius: 2, background: cfg?.color || 'var(--text-muted)', flexShrink: 0 }} />
              {d.name}: {d.total} (₹{d.recovered.toLocaleString('en-IN')})
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default memo(ActionBreakdownChartInner, (prev, next) => prev.data === next.data);
