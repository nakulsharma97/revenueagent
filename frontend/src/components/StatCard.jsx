const ACCENT_STYLES = {
  green: {
    color: 'var(--green)',
    bg: 'var(--green-bg)',
    border: '#A7F3D0',
  },
  blue: {
    color: 'var(--teal)',
    bg: 'var(--blue-bg)',
    border: '#BAE6FD',
  },
  gold: {
    color: 'var(--amber)',
    bg: 'var(--amber-bg)',
    border: '#FDE68A',
  },
};

export default function StatCard({ label, value, accent, sub }) {
  const style = ACCENT_STYLES[accent] || null;

  return (
    <div
      className="card"
      style={{
        flex: 1,
        minWidth: 180,
        padding: '20px 22px',
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        marginBottom: 10,
      }}>
        {style && (
          <div style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: style.color,
          }} />
        )}
        <div style={{
          fontSize: 12,
          color: 'var(--text-muted)',
          fontWeight: 500,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}>
          {label}
        </div>
      </div>

      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: 28,
        fontWeight: 700,
        color: style ? style.color : 'var(--text)',
        lineHeight: 1,
      }}>
        {value}
      </div>

      {sub && (
        <div style={{
          fontSize: 12,
          color: 'var(--text-muted)',
          marginTop: 8,
          lineHeight: 1.4,
        }}>
          {sub}
        </div>
      )}
    </div>
  );
}
