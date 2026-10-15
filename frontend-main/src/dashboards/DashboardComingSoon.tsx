/**
 * Placeholder for personas catalogued in `DASHBOARD_PLAN.md` but not
 * yet implemented (HR / Payroll / Admin land in D-5 / D-6 / D-7).
 * Renders the same chrome as a real dashboard — PageHeader + role
 * switcher — so users with multiple personas can flip back without
 * dead-ending.
 */
import { Card, Space, Typography, theme as antdTheme } from 'antd';
import {
  Briefcase,
  Hammer,
  ShieldCheck,
  Wallet,
  type LucideIcon,
} from 'lucide-react';

import { useAuth } from '@/auth';
import { EmptyState, PageHeader } from '@/components';

import { welcomeTitle } from './greeting';
import { PERSONA_LABEL, type DashboardPersona } from './persona';
import { RoleSwitcher } from './RoleSwitcher';

const { Text } = Typography;
const { useToken } = antdTheme;

const ICON: Record<DashboardPersona, LucideIcon> = {
  employee: Hammer,
  manager: Hammer,
  hr: Briefcase,
  payroll: Wallet,
  admin: ShieldCheck,
};

const PREVIEW_COPY: Record<DashboardPersona, { eyebrow: string; bullets: string[] }> = {
  employee: { eyebrow: 'Personal', bullets: [] },
  manager: { eyebrow: 'My team', bullets: [] },
  hr: {
    eyebrow: 'People ops',
    bullets: [
      'Org-wide attendance pulse + 30-day trend with anomaly markers',
      'Triage queues — onboarding stuck, contracts expiring, leave hotspots',
      'Headcount stacked area · joiners vs leavers, 12 months',
      'Department leave hotspots · sortable horizontal bar',
    ],
  },
  payroll: {
    eyebrow: 'Money',
    bullets: [
      'Current run state with cutoff countdown',
      'Run-over-run stacked bar · gross / deductions / net',
      'Anomaly scatter · employees outside variance threshold',
      'Held payslips queue · holds + release-pending across runs',
    ],
  },
  admin: {
    eyebrow: 'System',
    bullets: [
      'Saga health grid · per-saga 24h failure rate sparkline',
      'Audit event volume · 7-day line + drill-down',
      'Auth events by hour-of-day · catches credential-stuffing patterns',
      'Recent role / permission changes for review',
    ],
  },
};

export function DashboardComingSoon({ persona }: { persona: DashboardPersona }) {
  const { token } = useToken();
  const { user } = useAuth();
  const Icon = ICON[persona];
  const copy = PREVIEW_COPY[persona];
  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`${copy.eyebrow} · ${PERSONA_LABEL[persona]} dashboard`}
        title={welcomeTitle(user?.fullName)}
        description="A glimpse of what's coming. The route is reserved; the layout ships next."
        actions={<RoleSwitcher />}
      />
      <Card>
        <EmptyState
          size="lg"
          icon={Icon}
          title="Coming soon"
          description={`The ${PERSONA_LABEL[persona]} dashboard ships in a future iteration. The Employee and My-team views are live today — flip the switcher above to use them.`}
        />
        {copy.bullets.length > 0 ? (
          <div
            style={{
              marginTop: 8,
              padding: 16,
              borderRadius: token.borderRadius,
              background: token.colorFillTertiary,
            }}
          >
            <Text
              style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                color: token.colorTextSecondary,
              }}
            >
              When it lands you'll see
            </Text>
            <ul style={{ margin: '6px 0 0', paddingLeft: 18, color: token.colorText }}>
              {copy.bullets.map((b) => (
                <li key={b} style={{ marginBottom: 4, fontSize: 13, lineHeight: 1.5 }}>
                  {b}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </Card>
    </Space>
  );
}
