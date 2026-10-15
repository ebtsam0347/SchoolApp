// /account/delegations — manage approval delegations.
//
// Two segments:
//   * Outgoing — delegations I made to others (active + revoked history).
//     Create / extend-or-shorten / revoke. Open-ended supported.
//   * Incoming — delegations someone made TO me (read-only inbox view).
//
// Resolution is dynamic on the server: `assigned_to` on `approval_steps`
// is never rewritten when delegations land or revoke. The UI is the same
// shape regardless — listing the rows with active/closed states.
//
// HR with APPROVAL_OVERRIDE can also create delegations on a manager's
// behalf. The "On behalf of" toggle in the create drawer flips the
// endpoint; non-HR users don't see the toggle.
//
// All subcomponents (table, create drawer, edit drawer, candidate picker)
// live in src/auth/delegations/.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Segmented, Tag } from 'antd';
import {
  Inbox as InboxIcon,
  Plus,
  Send,
  ShieldCheck,
  UserCheck,
} from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { useAuth } from '@/auth';
import { delegationsApi, type DelegationDto } from '@/attendance';
import {
  DelegationCreateDrawer,
  DelegationEditDrawer,
  DelegationTable,
} from '@/auth/delegations';

export const Route = createFileRoute('/_app/account/delegations')({
  component: DelegationsPage,
});

type Tab = 'OUTGOING' | 'INCOMING';

function DelegationsPage() {
  const auth = useAuth();
  const callerUserId = auth.user?.userId ?? null;

  const [tab, setTab] = useState<Tab>('OUTGOING');
  const [openCreate, setOpenCreate] = useState(false);
  const [editing, setEditing] = useState<DelegationDto | null>(null);

  const minesQuery = useQuery({
    queryKey: ['delegations', 'me'],
    queryFn: ({ signal }) => delegationsApi.mine({}, signal),
  });

  const all = minesQuery.data ?? [];
  // Server returns delegations involving the caller (delegator OR delegate).
  // Split locally — incoming is read-only, outgoing is editable.
  const outgoing = callerUserId
    ? all.filter((d) => d.delegatorId === callerUserId)
    : [];
  const incoming = callerUserId
    ? all.filter((d) => d.delegateId === callerUserId)
    : [];

  const counts = {
    outgoingActive: outgoing.filter((d) => d.status === 'ACTIVE').length,
    incomingActive: incoming.filter((d) => d.status === 'ACTIVE').length,
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <PageHeader
        eyebrow="Account"
        title="Approval delegations"
        description="Hand approval authority to a teammate while you're away — or see who's covering for you."
        actions={
          <Button
            type="primary"
            icon={<Plus size={14} />}
            onClick={() => setOpenCreate(true)}
          >
            New delegation
          </Button>
        }
      />

      <Alert
        type="info"
        showIcon
        icon={<ShieldCheck size={16} />}
        title="Resolution is dynamic"
        description={
          <span>
            We don't rewrite approval-step assignees when you create or revoke a
            delegation. In-flight requests pick up the change on the next inbox
            read; past decisions are unaffected.
          </span>
        }
      />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={Send}
          label="My active delegations"
          value={`${counts.outgoingActive}`}
          delta={
            counts.outgoingActive === 0
              ? "You aren't delegating anyone right now"
              : undefined
          }
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={InboxIcon}
          label="Acting on behalf of"
          value={`${counts.incomingActive}`}
          delta={
            counts.incomingActive === 0
              ? 'Nobody has delegated authority to you'
              : 'managers'
          }
        />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={UserCheck}
          label="History rows"
          value={`${all.length}`}
          delta="active + revoked across both directions"
        />
      </KpiGrid>

      <Section
        title={tab === 'OUTGOING' ? 'Outgoing' : 'Incoming'}
        subtitle={
          tab === 'OUTGOING'
            ? 'Delegations you have made.'
            : 'Delegations made TO you. Read-only — only the delegator (or HR override) can revoke.'
        }
        actions={
          <Segmented
            value={tab}
            onChange={(v) => setTab(v as Tab)}
            options={[
              {
                value: 'OUTGOING',
                label: <SegmentLabel text="Outgoing" count={counts.outgoingActive} />,
              },
              {
                value: 'INCOMING',
                label: <SegmentLabel text="Incoming" count={counts.incomingActive} />,
              },
            ]}
          />
        }
      >
        <RequestState
          loading={false}
          error={minesQuery.isError ? minesQuery.error : undefined}
          empty={
            !minesQuery.isLoading &&
            (tab === 'OUTGOING' ? outgoing.length === 0 : incoming.length === 0)
          }
          errorTitle="Couldn't load delegations"
          emptyFallback={
            tab === 'OUTGOING' ? (
              <EmptyState
                icon={Send}
                title="No outgoing delegations"
                description="If you'll be unavailable, hand approvals to a teammate so requests don't sit waiting on you."
                action={
                  <Button
                    type="primary"
                    icon={<Plus size={14} />}
                    onClick={() => setOpenCreate(true)}
                  >
                    Create a delegation
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={InboxIcon}
                title="Nobody has delegated authority to you"
                description="When a manager hands you their approvals — temporarily or open-ended — it'll show up here."
              />
            )
          }
        >
          {tab === 'OUTGOING' ? (
            <DelegationTable
              data={outgoing}
              kind="OUTGOING"
              onEdit={(d) => setEditing(d)}
            />
          ) : (
            <DelegationTable
              data={incoming}
              kind="INCOMING"
              onEdit={() => undefined}
            />
          )}
        </RequestState>
      </Section>

      <DelegationCreateDrawer
        open={openCreate}
        onClose={() => setOpenCreate(false)}
      />
      <DelegationEditDrawer
        delegation={editing}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function SegmentLabel({ text, count }: { text: string; count: number }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      {text}
      {count > 0 ? <Tag style={{ margin: 0, fontSize: 11 }}>{count}</Tag> : null}
    </span>
  );
}
