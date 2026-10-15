// Vertical 3px bar that sits flush against the left edge of an active nav
// row. Painted in the active-icon accent so each theme stamps its own
// color on the indicator (vellum teal, klein lime, etc.).

export function ActiveBar() {
  return (
    <span
      aria-hidden="true"
      style={{
        position: 'absolute',
        left: 0,
        top: 6,
        bottom: 6,
        width: 3,
        borderRadius: 2,
        background: 'var(--app-sidebar-icon-active)',
      }}
    />
  );
}
