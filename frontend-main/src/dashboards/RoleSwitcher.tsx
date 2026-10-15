/**
 * Page-header switcher for users who qualify for multiple personas.
 * Hidden when only one persona is available (so the chrome doesn't lie
 * about a choice that doesn't exist).
 */
import { Segmented, Space, Typography, theme as antdTheme } from 'antd';
import {
  Briefcase,
  ShieldCheck,
  Sparkles,
  User,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';

import {
  IMPLEMENTED_PERSONAS,
  PERSONA_LABEL,
  PERSONA_ORDER,
  type DashboardPersona,
} from './persona';
import { useDashboardPersona } from './use-dashboard-persona';

const { Text } = Typography;
const { useToken } = antdTheme;

const ICON: Record<DashboardPersona, LucideIcon> = {
  employee: User,
  manager: Users,
  hr: Briefcase,
  payroll: Wallet,
  admin: ShieldCheck,
};

export function RoleSwitcher() {
  const { persona, available, setPersona } = useDashboardPersona();
  const { token } = useToken();

  // Render order: employee → manager → hr → payroll → admin (left → right
  // matches Personal → System scope progression). The resolver order
  // (admin → … → employee) is for *picking* a default, not display.
  const visible = [...PERSONA_ORDER]
    .reverse()
    .filter((p) => available.has(p));

  if (visible.length <= 1) return null;

  return (
    <Segmented
      size="small"
      value={persona}
      onChange={(value) => setPersona(value as DashboardPersona)}
      options={visible.map((p) => {
        const Icon = ICON[p];
        const implemented = IMPLEMENTED_PERSONAS.has(p);
        return {
          value: p,
          label: (
            <Space size={6} align="center" style={{ padding: '0 2px' }}>
              <Icon
                size={14}
                strokeWidth={2}
                color={p === persona ? token.colorPrimary : undefined}
                aria-hidden
              />
              <span>{PERSONA_LABEL[p]}</span>
              {!implemented ? (
                <Sparkles
                  size={11}
                  strokeWidth={2}
                  color={token.colorWarning}
                  aria-label="Preview"
                />
              ) : null}
            </Space>
          ),
        };
      })}
    />
  );
}

/**
 * Standalone caption for the page-header — "Viewing as Manager · 1 of 4
 * available". Useful for accessibility / quick comprehension when the
 * switcher itself is keyboard-navigated.
 */
export function RoleSwitcherCaption() {
  const { persona, available } = useDashboardPersona();
  if (available.size <= 1) return null;
  return (
    <Text type="secondary" style={{ fontSize: 11 }}>
      Viewing as {PERSONA_LABEL[persona]} · {available.size} dashboards available to you
    </Text>
  );
}
