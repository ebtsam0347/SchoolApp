// /people/$id/profile — Profile tab.
//
// Read-only display of contact / identity / personal / address fields,
// with a single "Edit profile" drawer covering the whole UpdateEmployeeRequest
// surface. Lives behind employee.write so view-only roles see the data
// without the edit affordance.

import { useState } from 'react';
import { createFileRoute, useParams } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Divider, Skeleton, Typography, theme as antdTheme } from 'antd';
import {
  Briefcase,
  Cake,
  GraduationCap,
  Heart,
  Home,
  IdCard,
  Mail,
  MapPin,
  Pencil,
  Phone,
  Plus,
  Sparkles,
  Star,
  User,
  type LucideIcon,
} from 'lucide-react';

import { peopleBff, formatCityWithProvince, formatCountry } from '@/people';
import {
  CertificationsPanel,
  ExperiencePanel,
  QualificationsPanel,
  SkillsPanel,
} from '@/people/background';
import { detailQueryKey } from '@/people/cache';
import { EditProfileDrawer } from '@/people/profile';
import { Can } from '@/auth';
import { ExpiryBadge, RequestState } from '@/components';
import { formatDateShort } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/$id/profile')({
  component: ProfileTab,
});

function ProfileTab() {
  const { id } = useParams({ from: '/_app/people/$id/profile' });
  const employeeId = Number(id);
  const [editing, setEditing] = useState(false);

  const detailQuery = useQuery({
    queryKey: detailQueryKey(employeeId),
    queryFn: ({ signal }) => peopleBff.detail(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  return (
    <>
      <RequestState
        loading={detailQuery.isLoading}
        error={detailQuery.error}
        empty={!detailQuery.data}
        loadingFallback={<Skeleton active paragraph={{ rows: 6 }} />}
        errorTitle="Couldn't load profile"
        emptyTitle="Profile not found"
        emptyDescription="The employee may have been removed, or the link is wrong."
      >
        {detailQuery.data ? (
          <ProfileBody
            detail={detailQuery.data}
            onEdit={() => setEditing(true)}
          />
        ) : null}
      </RequestState>

      {detailQuery.data?.employee ? (
        <EditProfileDrawer
          open={editing}
          onClose={() => setEditing(false)}
          employee={detailQuery.data.employee}
        />
      ) : null}
    </>
  );
}

function ProfileBody({
  detail,
  onEdit,
}: {
  detail: NonNullable<Awaited<ReturnType<typeof peopleBff.detail>>>;
  onEdit: () => void;
}) {
  const e = detail.employee;
  const r = detail.resolved;

  const cityLabel = r?.city?.name
    ? formatCityWithProvince(r.city.name, r.city.provinceCode)
    : null;

  const employeeId = e?.id ?? 0;

  // Add-modal state lifted up from each panel so the Add buttons can sit
  // in the surrounding card header — they stay visible when the body
  // scrolls.
  const [qualOpen, setQualOpen] = useState(false);
  const [expOpen, setExpOpen] = useState(false);
  const [skillOpen, setSkillOpen] = useState(false);
  const [certOpen, setCertOpen] = useState(false);

  return (
    // Two super-sections — Identity (one card, definition list) and
    // Background (four cards in a 2-col grid). Cards give containment
    // so subsections don't bleed into each other, and the definition list
    // shows "—" for empty fields instead of leaving headers floating.
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Card
        size="small"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <User size={15} strokeWidth={1.75} />
            <span style={{ fontWeight: 600 }}>Identity</span>
          </span>
        }
        extra={
          <Can I="write" a="employee">
            <Button
              type="primary"
              size="small"
              icon={<Pencil size={14} strokeWidth={1.75} />}
              onClick={onEdit}
            >
              Edit profile
            </Button>
          </Can>
        }
        styles={{ body: { padding: '8px 16px 16px' } }}
      >
        <IdentityGroup label="Contact">
          <DefRow icon={Phone} label="Phone" value={e?.phone} mono />
          <DefRow icon={Mail} label="Work email" value={e?.workEmail} mono />
          <DefRow icon={Mail} label="Personal email" value={e?.personalEmail} mono />
          <DefRow icon={MapPin} label="City" value={cityLabel} />
          <DefRow icon={Home} label="Permanent address" value={e?.permanentAddress} />
          {e?.currentAddress && e.currentAddress !== e.permanentAddress ? (
            <DefRow icon={Home} label="Current address" value={e.currentAddress} />
          ) : null}
        </IdentityGroup>

        <IdentityGroup label="Identification">
          <DefRow icon={IdCard} label="CNIC" value={e?.cnic} mono />
          <DefRow
            label="CNIC issued"
            value={e?.cnicIssueDate ? formatDateShort(e.cnicIssueDate) : null}
          />
          <DefRow
            label="CNIC expiry"
            value={
              e?.cnicExpiryDate ? (
                <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                  {formatDateShort(e.cnicExpiryDate)}
                  <ExpiryBadge date={e.cnicExpiryDate} mode="expiry" />
                </span>
              ) : null
            }
          />
          <DefRow label="NTN" value={e?.ntn} mono />
          <DefRow label="EOBI" value={e?.eobiNumber} mono />
          <DefRow label="SSC / PESSI" value={e?.sscNumber} mono />
          <DefRow label="Insurance #" value={e?.insuranceNumber} mono />
        </IdentityGroup>

        <IdentityGroup label="Personal" last>
          <DefRow
            icon={Cake}
            label="Date of birth"
            value={e?.dateOfBirth ? formatDateShort(e.dateOfBirth) : null}
          />
          <DefRow icon={User} label="Gender" value={e?.gender ? toTitle(e.gender) : null} />
          <DefRow
            icon={Heart}
            label="Marital status"
            value={e?.maritalStatus ? toTitle(e.maritalStatus) : null}
          />
          <DefRow label="Country" value={formatCountry(e?.country ?? 'PK')} />
        </IdentityGroup>
      </Card>

      <div
        style={{
          display: 'grid',
          // Capped at 2 columns on desktop via max(420, 50%-gap/2). Falls
          // back to 1 column under ~860px.
          gridTemplateColumns: 'repeat(auto-fit, minmax(max(420px, calc(50% - 10px)), 1fr))',
          gap: 16,
        }}
      >
        <BackgroundCard
          label="Qualifications"
          icon={GraduationCap}
          onAdd={() => setQualOpen(true)}
        >
          <QualificationsPanel
            employeeId={employeeId}
            addOpen={qualOpen}
            onAddOpenChange={setQualOpen}
          />
        </BackgroundCard>
        <BackgroundCard
          label="Experience"
          icon={Briefcase}
          onAdd={() => setExpOpen(true)}
        >
          <ExperiencePanel
            employeeId={employeeId}
            addOpen={expOpen}
            onAddOpenChange={setExpOpen}
          />
        </BackgroundCard>
        <BackgroundCard
          label="Skills"
          icon={Star}
          onAdd={() => setSkillOpen(true)}
        >
          <SkillsPanel
            employeeId={employeeId}
            addOpen={skillOpen}
            onAddOpenChange={setSkillOpen}
          />
        </BackgroundCard>
        <BackgroundCard
          label="Certifications"
          icon={Sparkles}
          onAdd={() => setCertOpen(true)}
        >
          <CertificationsPanel
            employeeId={employeeId}
            addOpen={certOpen}
            onAddOpenChange={setCertOpen}
          />
        </BackgroundCard>
      </div>
    </div>
  );
}

/** A labeled group inside the Identity card. Adds an uppercase eyebrow
 *  label and a thin divider between groups, except the last. */
function IdentityGroup({
  label,
  children,
  last,
}: {
  label: string;
  children: React.ReactNode;
  last?: boolean;
}) {
  const { token } = useToken();
  return (
    <>
      <div style={{ paddingTop: 12, paddingBottom: 4 }}>
        <div
          style={{
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.6,
            textTransform: 'uppercase',
            color: token.colorTextTertiary,
            marginBottom: 8,
          }}
        >
          {label}
        </div>
        <DefList>{children}</DefList>
      </div>
      {!last ? <Divider style={{ margin: 0 }} /> : null}
    </>
  );
}

/** Containment wrapper for the four background panels. Fixed body height
 *  with internal scroll so the four cards stay vertically balanced even
 *  if one section grows. The Add button lives in the card header (not
 *  inside the scrollable body) so it stays visible after scrolling. */
function BackgroundCard({
  label,
  icon: Icon,
  onAdd,
  children,
}: {
  label: string;
  icon: LucideIcon;
  onAdd?: () => void;
  children: React.ReactNode;
}) {
  const { token } = useToken();
  return (
    <Card
      size="small"
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Icon size={15} strokeWidth={1.75} color={token.colorTextSecondary} />
          <span style={{ fontWeight: 600 }}>{label}</span>
        </span>
      }
      extra={
        onAdd ? (
          <Button
            size="small"
            icon={<Plus size={14} strokeWidth={1.75} />}
            onClick={onAdd}
          >
            Add
          </Button>
        ) : null
      }
      styles={{ body: { height: 460, overflowY: 'auto', padding: 16 } }}
    >
      {children}
    </Card>
  );
}

function DefList({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>{children}</div>
  );
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
  const isEmpty = value === undefined || value === null || value === '';
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '160px 1fr',
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
      {isEmpty ? (
        <Text type="secondary" style={{ fontSize: 13 }}>
          —
        </Text>
      ) : (
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
      )}
    </div>
  );
}

function toTitle(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}
