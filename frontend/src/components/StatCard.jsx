export default function StatCard({ label, value, sub, icon, iconBg, iconColor, valueColor }) {
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius-md)',
      padding: '16px 20px',
      width: '100%',
      minWidth: 0,
      transition: 'border-color var(--transition-fast)',
    }}
      onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--border-strong)'}
      onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <div style={{
          width: 36, height: 36, borderRadius: '50%',
          background: iconBg || 'var(--surface-elevated)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 16, flexShrink: 0,
        }}>
          <span style={{ color: iconColor || 'var(--text-muted)' }}>{icon}</span>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>{label}</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 24, fontWeight: 700, color: valueColor || 'var(--text)', lineHeight: 1.1 }}>{value}</div>
          {sub && <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>{sub}</div>}
        </div>
      </div>
    </div>
  );
}
