// /account/employment — ESS employee record slice.
//
// Single composite read against /bff/account/profile (employee + resolved
// labels + active contract + team preview). Mirrors the HR detail page's
// Overview tab but read-only — HR maintains the underlying record.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Card,
  Empty,
  Skeleton,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  CalendarClock,
  ExternalLink,
  FileText,
  Lock,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';

import {
  ExpiryBadge,
  OrgPath,
  PageHeader,
  PersonCell,
  Section,
  StatusPill,
} from '@/components';
import { getErrorMessage, isForbidden, isNotFound } from '@/lib';
import {
  CONTRACT_TYPE_LABEL,
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_STATUS_TONE,
  EMPLOYMENT_TYPE_LABEL,
  formatCityWithProvince,
  peopleBff,
} from '@/people';

const { Text, Paragraph } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/employment')({
  component: EmploymentSelf,
});

function EmploymentSelf() {
  const { token } = useToken();
  const profileQuery = useQuery({
    queryKey: ['bff', 'account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
  });

  if (profileQuery.isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  if (profileQuery.isError) {
    const forbidden = isForbidden(profileQuery.error);
    const notFound = isNotFound(profileQuery.error);
    return (
      <Alert
        type={forbidden || notFound ? 'info' : 'error'}
        showIcon
        title={
          notFound
            ? "You don't have an employee record on file."
            : forbidden
              ? "You don't have permission to view your employee record."
              : "Couldn't load employee record"
        }
        description={
          notFound
            ? 'Ask HR to link your account to your employee record.'
            : getErrorMessage(profileQuery.error)
        }
      />
    );
  }

  const profile = profileQuery.data;
  if (!profile) return null;

  const e = profile.employee;
  const r = profile.resolved;
  const contract = profile.activeContract;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="Your record"
        title="Employment"
        description="HR-maintained employee record. Identity numbers and contracts are read-only here — contact HR for changes."
        actions={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: token.colorTextSecondary }}>
            <Lock size={14} strokeWidth={1.75} />
            HR maintains
          </span>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
            <strong style={{ fontSize: 16 }}>{e?.fullName ?? '—'}</strong>
            <Text type="secondary" style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
              {e?.employeeCode}
            </Text>
            {e?.employmentStatus ? (
              <StatusPill tone={EMPLOYMENT_STATUS_TONE[e.employmentStatus]}>
                {EMPLOYMENT_STATUS_LABEL[e.employmentStatus]}
              </StatusPill>
            ) : null}
          </div>
          <div style={{ marginBottom: 12 }}>
            <OrgPath
              segments={[
                r?.department?.name ? { label: r.department.name } : null,
                r?.section?.name ? { label: r.section.name } : null,
              ]}
            />
          </div>
          <DefList>
            <DefRow label="Designation" value={r?.designation?.title} />
            <DefRow label="Grade" value={r?.grade?.name} />
            <DefRow
              label="Type"
              value={e?.employmentType ? EMPLOYMENT_TYPE_LABEL[e.employmentType] : null}
            />
            <DefRow
              label="Joined"
              value={e?.joiningDate ? formatDate(e.joiningDate) : null}
            />
            {e?.confirmationDate ? (
              <DefRow label="Confirmed" value={formatDate(e.confirmationDate)} />
            ) : null}
          </DefList>
        </Card>

        <Card title="Contact">
          <DefList>
            <DefRow icon={Phone} label="Phone" value={e?.phone} mono />
            <DefRow icon={Mail} label="Work email" value={e?.workEmail} mono />
            <DefRow icon={Mail} label="Personal email" value={e?.personalEmail} mono />
            <DefRow
              icon={MapPin}
              label="City"
              value={
                r?.city?.name
                  ? formatCityWithProvince(r.city.name, r.city.provinceCode)
                  : null
              }
            />
            <DefRow label="Permanent" value={e?.permanentAddress} />
            <DefRow label="Current" value={e?.currentAddress} />
          </DefList>
        </Card>

        <Card title="Identity numbers">
          <DefList>
            <DefRow label="CNIC" value={e?.cnic} mono />
            <DefRow label="NTN" value={e?.ntn} mono />
            <DefRow label="EOBI" value={e?.eobiNumber} mono />
            <DefRow label="PESSI / SSC" value={e?.sscNumber} mono />
            <DefRow label="Insurance" value={e?.insuranceNumber} mono />
          </DefList>
        </Card>

        <Section title="Manager">
          {r?.manager?.id ? (
            <PersonCell
              size="default"
              person={{
                fullName: r.manager.fullName ?? '—',
                employeeCode: r.manager.employeeCode,
                profilePictureUrl: r.manager.profilePictureUrl,
              }}
            />
          ) : (
            <Text type="secondary">No manager assigned.</Text>
          )}
        </Section>

        {contract ? (
          <Section title="Contract">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '4px 10px',
                    background: token.colorFillQuaternary,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    borderRadius: 999,
                    fontSize: 12,
                  }}
                >
                  <ShieldCheck size={14} color={token.colorPrimary} />
                  {contract.contractType
                    ? CONTRACT_TYPE_LABEL[contract.contractType]
                    : 'Contract'}
                </span>
                {contract.extended ? <Tag color="warning">Extended</Tag> : null}
                {contract.documentUrl ? (
                  <a
                    href={contract.documentUrl}
                    target="_blank"
                    rel="noreferrer"
                    style={{ display: 'inline-flex', gap: 4, alignItems: 'center', fontSize: 12 }}
                  >
                    <FileText size={14} /> Open <ExternalLink size={12} />
                  </a>
                ) : null}
              </div>
              <DefList>
                <DefRow
                  label="Started"
                  value={contract.startDate ? formatDate(contract.startDate) : null}
                />
                {profile.upcomingProbationDeadline ? (
                  <DefRow
                    icon={CalendarClock}
                    label="Probation due"
                    value={
                      <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                        {formatDate(profile.upcomingProbationDeadline)}
                        <ExpiryBadge date={profile.upcomingProbationDeadline} mode="deadline" />
                      </span>
                    }
                  />
                ) : contract.endDate ? (
                  <DefRow label="Ends" value={formatDate(contract.endDate)} />
                ) : null}
              </DefList>
            </div>
          </Section>
        ) : null}

        <Section title={`Direct reports${profile.team?.length ? ` · ${profile.team.length}` : ''}`}>
          {(profile.team?.length ?? 0) === 0 ? (
            <Empty description="No reports" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {profile.team!.map((r) => (
                <div key={r.id} style={{ padding: '6px 0' }}>
                  <PersonCell
                    size="compact"
                    person={{
                      fullName: r.fullName ?? '—',
                      employeeCode: r.employeeCode,
                      profilePictureUrl: r.profilePictureUrl,
                    }}
                  />
                </div>
              ))}
            </div>
          )}
        </Section>
      </div>

      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        Need a change? <Link to="/account">Open Profile</Link> for self-serve
        contact updates, or contact HR for everything else (designation, grade,
        identity numbers, address). Document uploads land in your Documents tab
        once that lands here.
      </Paragraph>
    </div>
  );
}

function DefList({ children }: { children: React.ReactNode }) {
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>{children}</div>;
}

function DefRow({
  icon: Icon,
  label,
  value,
  mono,
}: {
  icon?: LucideIcon;
  label: string;
  value?: React.ReactNode;
  mono?: boolean;
}) {
  const { token } = useToken();
  if (value === undefined || value === null || value === '') return null;
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '140px 1fr',
        alignItems: 'baseline',
        gap: 12,
        padding: '4px 0',
      }}
    >
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 12,
          color: token.colorTextSecondary,
        }}
      >
        {Icon ? <Icon size={14} strokeWidth={1.75} aria-hidden /> : null}
        {label}
      </span>
      <span
        style={{
          fontSize: 13,
          color: token.colorTextHeading,
          fontFamily: mono
            ? 'ui-monospace, SFMono-Regular, Menlo, monospace'
            : undefined,
          wordBreak: 'break-word',
        }}
      >
        {value}
      </span>
    </div>
  );
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-PK', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value));
}
