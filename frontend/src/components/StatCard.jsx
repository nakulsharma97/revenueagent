export default function StatCard({ label, value, sub, icon, iconBg, iconColor, valueColor }) {
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius-md)',
      boxShadow: 'var(--shadow-xs)',
      padding: '20px 24px',
    }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14 }}>
        <div style={{
          width: 42,
          height: 42,
          borderRadius: 'var(--radius-sm)',
          background: iconBg || '#F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 18,
          flexShrink: 0,
        }}>
          <span style={{ color: iconColor || 'var(--text-muted)' }}>{icon}</span>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{
            fontFamily: 'var(--font-body)',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: 'var(--text-muted)',
            marginBottom: 4,
          }}>
            {label}
          </div>
          <div style={{
            fontFamily: 'var(--font-display)',
            fontSize: 28,
            fontWeight: 700,
            color: valueColor || 'var(--text)',
            lineHeight: 1.1,
          }}>
            {value}
          </div>
          {sub && (
            <div style={{
              fontFamily: 'var(--font-body)',
              fontSize: 12,
              color: 'var(--text-muted)',
              marginTop: 4,
            }}>
              {sub}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
