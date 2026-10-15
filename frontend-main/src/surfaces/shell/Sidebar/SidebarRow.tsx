// One nav row. Three behaviours layered on:
//   - leaf row → click selects.
//   - parent row, expanded layout → click toggles SubMenu beneath.
//   - parent row, rail layout → hover opens FlyoutPanel to the right.
//
// Keyboard: ArrowRight expands a parent, ArrowLeft collapses it. The
// up/down nav between rows is owned by Sidebar.tsx (handleKeyDown).

import { useEffect, useState, type KeyboardEvent } from 'react';
import { ChevronDown } from 'lucide-react';
import { Popover, Tooltip } from 'antd';
import { useMediaQuery } from '@/lib';
import type { NavItem } from '../nav-config';
import { ActiveBar } from './ActiveBar';
import { FlyoutPanel } from './FlyoutPanel';
import { NavBadgeDot, NavBadgePill, NavCountBadge, NavCountDot } from './NavBadge';
import { SubMenu } from './SubMenu';
import { isItemActive } from './nav-ability';

export type SidebarRowProps = {
  item: NavItem;
  collapsed: boolean;
  activeKey: string;
  onSelect: (key: string) => void;
};

export function SidebarRow({ item, collapsed, activeKey, onSelect }: SidebarRowProps) {
  const hasChildren = !!item.children?.length;
  const containsActive = hasChildren && isItemActive(item, activeKey);
  const [expanded, setExpanded] = useState(containsActive);
  const [flyoutOpen, setFlyoutOpen] = useState(false);
  // Touch devices report `pointer: coarse`. On those, hover-to-open feels
  // sticky (the hover state lingers after a tap), so we switch the flyout to
  // a click trigger and dismiss it after the user picks a child.
  const isTouch = useMediaQuery('(pointer: coarse)');

  useEffect(() => {
    if (containsActive) setExpanded(true);
  }, [containsActive]);

  // Close flyout whenever active route changes (covers select-then-navigate).
  useEffect(() => {
    setFlyoutOpen(false);
  }, [activeKey]);

  const active = item.key === activeKey;
  const Icon = item.icon;

  const onActivate = () => {
    if (hasChildren) setExpanded((v) => !v);
    else onSelect(item.key);
  };

  const onRowKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (!hasChildren) return;
    if (e.key === 'ArrowRight' && !expanded) {
      e.preventDefault();
      setExpanded(true);
    } else if (e.key === 'ArrowLeft' && expanded) {
      e.preventDefault();
      setExpanded(false);
    }
  };

  const row = (
    <button
      type="button"
      data-nav-row
      onClick={onActivate}
      onKeyDown={onRowKeyDown}
      aria-expanded={hasChildren ? expanded : undefined}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        height: 36,
        padding: collapsed ? '0 18px' : '0 10px',
        borderRadius: 'var(--app-radius-sm)',
        border: 'none',
        background: active ? 'var(--app-sidebar-bg-active)' : 'transparent',
        color: active ? 'var(--app-sidebar-text-active)' : 'var(--app-sidebar-text)',
        fontSize: 13,
        fontWeight: active ? 500 : 400,
        cursor: 'pointer',
        textAlign: 'left',
        position: 'relative',
        transition: 'background var(--app-transition-fast)',
      }}
      onMouseEnter={(e) => {
        if (!active) e.currentTarget.style.background = 'var(--app-sidebar-bg-hover)';
      }}
      onMouseLeave={(e) => {
        if (!active) e.currentTarget.style.background = 'transparent';
      }}
    >
      {active && <ActiveBar />}
      {Icon && (
        <Icon
          size={18}
          strokeWidth={active ? 2 : 1.75}
          color={active ? 'var(--app-sidebar-icon-active)' : 'currentColor'}
          style={{ flexShrink: 0 }}
        />
      )}
      {!collapsed && (
        <span
          style={{
            flex: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {item.label}
        </span>
      )}
      {!collapsed && item.countSuffix ? <NavCountBadge count={item.countSuffix} /> : null}
      {!collapsed && item.badge ? <NavBadgePill badge={item.badge} /> : null}
      {!collapsed && hasChildren && (
        <ChevronDown
          size={14}
          style={{
            transition: 'transform var(--app-transition-normal)',
            transform: expanded ? 'rotate(180deg)' : 'none',
            opacity: 0.7,
          }}
        />
      )}
      {collapsed && item.countSuffix ? (
        <NavCountDot count={item.countSuffix} />
      ) : collapsed && item.badge ? (
        <NavBadgeDot tone={item.badge.tone} />
      ) : null}
    </button>
  );

  return (
    <li>
      {collapsed ? (
        hasChildren ? (
          <Popover
            placement="rightTop"
            trigger={isTouch ? 'click' : 'hover'}
            open={flyoutOpen}
            onOpenChange={setFlyoutOpen}
            mouseEnterDelay={0.1}
            arrow={false}
            styles={{ container: { padding: 6, background: 'var(--app-sidebar-bg-elevated)' } }}
            content={
              <FlyoutPanel
                item={item}
                activeKey={activeKey}
                onSelect={(key) => {
                  setFlyoutOpen(false);
                  onSelect(key);
                }}
              />
            }
          >
            {row}
          </Popover>
        ) : (
          <Tooltip title={item.label} placement="right">
            {row}
          </Tooltip>
        )
      ) : (
        row
      )}
      {!collapsed && hasChildren && (
        <SubMenu
          items={item.children!}
          activeKey={activeKey}
          expanded={expanded}
          onSelect={onSelect}
        />
      )}
    </li>
  );
}
