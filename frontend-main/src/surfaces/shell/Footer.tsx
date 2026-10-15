export function Footer() {
  const env = (import.meta.env.VITE_ENV ?? 'dev') as string;
  return (
    <footer
      style={{
        gridArea: 'footer',
        height: 32,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '0 20px',
        fontSize: 11,
        color: 'var(--app-footer-text)',
        background: 'var(--app-footer-bg)',
        borderTop: '1px solid var(--app-footer-border)',
      }}
    >
      <span>v0.1.0</span>
      <Sep />
      <span>{env}</span>
      <Sep />
      <span>© The Spirit of Islam School 2026</span>
      <div style={{ flex: 1 }} />
      <a
        href="#"
        style={{ color: 'var(--app-footer-text)', textDecoration: 'none' }}
      >
        Status ▴
      </a>
    </footer>
  );
}

function Sep() {
  return <span style={{ opacity: 0.5 }}>·</span>;
}
