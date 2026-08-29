const ACCENT_COLORS = {
  green: 'var(--ink-green)',
  blue: 'var(--ink-blue)',
  gold: 'var(--ink-amber)',
};

export default function StatCard({ label, value, accent, sub }) {
  const color = ACCENT_COLORS[accent] || 'var(--text)';

  return (
    <div style={{
      flex: 1,
      minWidth: 180,
      padding: '14px 18px',
      borderRight: 'var(--rule)',
      borderBottom: 'var(--rule)',
    }}>
      <div style={{
        fontFamily: 'var(--font-display)',
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color: 'var(--text-muted)',
        marginBottom: 6,
      }}>
        {label}
      </div>
      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: 26,
        fontWeight: 700,
        color: color,
        lineHeight: 1,
        letterSpacing: '-0.02em',
      }}>
        {value}
      </div>
      {sub && (
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: 11,
          color: 'var(--text-muted)',
          marginTop: 6,
          lineHeight: 1.4,
        }}>
          {sub}
        </div>
      )}
    </div>
  );
}
