// 36×36 icon button used by every topbar control (mobile menu, collapse
// toggle, search trigger in compact mode, quick actions). Three styles:
//
//   variant='ghost'   — transparent, no border (mobile menu, collapse)
//   variant='outline' — transparent + 1px border (compact search, quick actions)
//   variant='solid'   — bordered, accent on hover (reserved for CTA)
//
// Always wrapped in a Tooltip so screen-readers and pointer users see the
// label.

import { Tooltip } from 'antd';
import type { ButtonHTMLAttributes, Ref } from 'react';

export type TopbarIconButtonProps = Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'aria-label' | 'type'
> & {
  /** aria-label and tooltip text. Required for accessibility. */
  label: string;
  /** Tooltip placement override. Defaults to 'bottom'. */
  tooltipPlacement?: 'top' | 'bottom' | 'left' | 'right';
  /** Hide the tooltip — useful when the button is inside another popover. */
  hideTooltip?: boolean;
  /** Visual treatment. Defaults to 'ghost'. */
  variant?: 'ghost' | 'outline';
  ref?: Ref<HTMLButtonElement>;
};

export function TopbarIconButton({
  label,
  tooltipPlacement = 'bottom',
  hideTooltip,
  variant = 'ghost',
  children,
  className,
  style,
  ref,
  ...rest
}: TopbarIconButtonProps) {
  const button = (
    <button
      ref={ref}
      type="button"
      aria-label={label}
      className={className}
      {...rest}
      style={{
        width: 36,
        height: 36,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'transparent',
        border: variant === 'outline' ? '1px solid var(--app-topbar-border)' : 'none',
        borderRadius: 'var(--app-radius-sm)',
        cursor: 'pointer',
        color:
          variant === 'outline' ? 'var(--app-topbar-text)' : 'var(--app-topbar-text-muted)',
        flexShrink: 0,
        ...style,
      }}
    >
      {children}
    </button>
  );

  if (hideTooltip) return button;
  return (
    <Tooltip title={label} placement={tooltipPlacement}>
      {button}
    </Tooltip>
  );
}
