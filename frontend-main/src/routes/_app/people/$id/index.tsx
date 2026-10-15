// /people/$id — Overview tab.
//
// Employment + reporting summary cards. Identity / contact / personal
// data lives on the Profile tab (which owns the edit drawer too). Reads
// the same composite as the parent layout (queryKey-shared via React
// Query cache).

import { useState } from 'react';
import {
  createFileRoute,
  Link,
  useParams,
} from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Popconfirm,
  Skeleton,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  ArrowRight,
  CalendarClock,
  ExternalLink,
  Eye,
  FileText,
  History as HistoryIcon,
  Package,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';

import { peopleBff, type EmployeeHistoryRow } from '@/people';
import { CONTRACT_TYPE_LABEL } from '@/people/types';
import {
  ExpiryBadge,
  PersonCell,
  StatusPill,
} from '@/components';
import {
  ACTIVATION_STATUS_LABEL,
  ACTIVATION_STATUS_TONE,
  AssetDetailModal,
  belongingIssuanceApi,
  type BelongingIssuanceDto,
} from '@/belongings';
import { getErrorMessage } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/$id/')({
  component: OverviewTab,
});

function OverviewTab() {
  const { id } = useParams({ from: '/_app/people/$id/' });
  const employeeId = Number(id);

  const detailQuery = useQuery({
    queryKey: ['people', 'detail', employeeId],
    queryFn: ({ signal }) => peopleBff.detail(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  // Same queryKey the "Allocate asset" action invalidates on success, so a
  // freshly issued asset shows up here without a manual refresh. Not
  // filtered by activationStatus — inactive rows stay visible here too so
  // they can be reactivated from this card.
  const assetsQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeActive(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });
  const assets = assetsQuery.data ?? [];
  const activeAssetCount = assets.filter((r) => r.activationStatus !== 'INACTIVE').length;

  if (detailQuery.isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  const detail = detailQuery.data;
  if (!detail) return null;

  const e = detail.employee;
  const contract = detail.activeContract;
  const reports = detail.directReportsPreview;
  // Use the canonical directReportsCount from the employee record so the
  // card title agrees with the header's "Manages N". The preview's `total`
  // can be a capped value depending on the BFF query, which would otherwise
  // disagree with the header for managers with > N reports.
  const directCount = e?.directReportsCount ?? reports?.total;

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: 16,
        alignItems: 'start',
      }}
    >
      <Card
        title="Current contract"
        size="small"
        extra={
          <Link to="/people/$id/employment" params={{ id }}>
            <Button type="link" size="small" icon={<ArrowRight size={14} />}>
              Contract history
            </Button>
          </Link>
        }
      >
        {contract ? (
          <ContractSummary contract={contract} probationEnd={e?.activeProbation?.endDate} />
        ) : (
          <Empty description="No active contract on file" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      <Card
        title={`Direct reports${directCount ? ` · ${directCount}` : ''}`}
        size="small"
        extra={
          directCount && directCount > 0 ? (
            <Link to="/people/$id/employment" params={{ id }}>
              <Button type="link" size="small" icon={<ArrowRight size={14} />}>
                View team
              </Button>
            </Link>
          ) : null
        }
      >
        {reports?.items && reports.items.length > 0 ? (
          <ReportsPreview reports={reports.items} />
        ) : (
          <Empty
            description="No direct reports"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </Card>

      <Card
        title={`Assets${activeAssetCount ? ` · ${activeAssetCount}` : ''}`}
        size="small"
        loading={assetsQuery.isLoading}
      >
        {assets.length === 0 ? (
          <Empty description="No allocated assets" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <AssetsPreview assets={assets} />
        )}
      </Card>

      <Card
        title="Recent changes"
        size="small"
        extra={
          (detail.recentHistory?.length ?? 0) > 0 ? (
            <Link to="/people/$id/employment" params={{ id }}>
              <Button type="link" size="small" icon={<ArrowRight size={14} />}>
                Full history
              </Button>
            </Link>
          ) : null
        }
      >
        {(detail.recentHistory?.length ?? 0) === 0 ? (
          <Empty
            description="No recorded org changes"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <RecentHistoryPreview rows={detail.recentHistory ?? []} />
        )}
      </Card>
    </div>
  );
}

function RecentHistoryPreview({ rows }: { rows: EmployeeHistoryRow[] }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      {rows.map((row) => (
        <RecentHistoryRow key={row.id} row={row} />
      ))}
    </div>
  );
}

function RecentHistoryRow({ row }: { row: EmployeeHistoryRow }) {
  // One-line summary picking whichever resolved field is most informative
  // for this change. Falls back to "Updated" if nothing distinguishing is
  // present.
  const summary = (() => {
    const parts: string[] = [];
    if (row.designation?.title) parts.push(row.designation.title);
    if (row.grade?.name) parts.push(row.grade.name);
    if (row.department?.name) parts.push(row.department.name);
    if (row.section?.name) parts.push(`/ ${row.section.name}`);
    if (row.manager?.fullName) parts.push(`under ${row.manager.fullName}`);
    return parts.join(' · ') || 'Updated';
  })();

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '110px 1fr',
        alignItems: 'baseline',
        gap: 12,
        padding: '6px 0',
        borderBottom: '1px solid var(--ant-color-border-secondary)',
      }}
    >
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
        <HistoryIcon size={12} strokeWidth={1.75} />
        {row.effectiveFrom ? formatDate(row.effectiveFrom) : '—'}
      </span>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          {row.changeReason ? (
            <Tag style={{ margin: 0 }}>
              {row.changeReason.replace(/_/g, ' ').toLowerCase()}
            </Tag>
          ) : null}
          {row.effectiveTo === null || row.effectiveTo === undefined ? (
            <Tag color="success" style={{ margin: 0 }}>Current</Tag>
          ) : null}
        </div>
        <Text style={{ fontSize: 13 }}>{summary}</Text>
        {row.notes ? (
          <Text type="secondary" style={{ fontSize: 12 }}>{row.notes}</Text>
        ) : null}
      </div>
    </div>
  );
}

/* --------------------- subcomponents --------------------- */

function ContractSummary({
  contract,
  probationEnd,
}: {
  contract: NonNullable<ReturnType<typeof _useDetailContract>>;
  probationEnd?: string;
}) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
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
            fontWeight: 500,
            color: token.colorTextHeading,
          }}
        >
          <ShieldCheck size={14} strokeWidth={1.75} color={token.colorPrimary} />
          {contract.contractType ? CONTRACT_TYPE_LABEL[contract.contractType] : 'Contract'}
        </span>
        {contract.extended ? <Tag color="warning">Extended</Tag> : null}
        {contract.documentUrl ? (
          <a
            href={contract.documentUrl}
            target="_blank"
            rel="noreferrer"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
            }}
          >
            <FileText size={14} /> View signed PDF
            <ExternalLink size={12} />
          </a>
        ) : null}
      </div>

      <DefList>
        <DefRow
          label="Started"
          value={contract.startDate ? formatDate(contract.startDate) : null}
        />
        {contract.contractType === 'PROBATION' && probationEnd ? (
          <DefRow
            icon={CalendarClock}
            label="Probation due"
            value={
              <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                {formatDate(probationEnd)}
                <ExpiryBadge date={probationEnd} mode="deadline" />
              </span>
            }
          />
        ) : contract.endDate ? (
          <DefRow
            label="Ends"
            value={
              <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                {formatDate(contract.endDate)}
                <ExpiryBadge date={contract.endDate} mode="expiry" />
              </span>
            }
          />
        ) : null}
        <DefRow
          label="Status"
          value={contract.status ? <StatusPill tone="info">{contract.status}</StatusPill> : null}
        />
      </DefList>
    </div>
  );
}

function ReportsPreview({
  reports,
}: {
  reports: NonNullable<NonNullable<Awaited<ReturnType<typeof peopleBff.detail>>['directReportsPreview']>['items']>;
}) {
  // PersonCell already renders an avatar per row; the previous AvatarStack
  // header was redundant (every face appeared twice).
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {reports.slice(0, 8).map((r) =>
        r.id ? (
          <div key={r.id} style={{ padding: '6px 0' }}>
            <PersonCell
              size="compact"
              person={{
                fullName: r.fullName ?? '—',
                employeeCode: r.employeeCode,
                profilePictureUrl: r.profilePictureUrl,
              }}
              to={`/people/${r.id}`}
              trailing={
                r.directReportsCount && r.directReportsCount > 0 ? (
                  <Tag style={{ margin: 0 }}>{r.directReportsCount} reports</Tag>
                ) : null
              }
            />
          </div>
        ) : null,
      )}
    </div>
  );
}

function AssetsPreview({ assets }: { assets: BelongingIssuanceDto[] }) {
  const [selectedAsset, setSelectedAsset] = useState<BelongingIssuanceDto | null>(null);

  return (
    <>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {assets.map((a) => (
          <div
            key={a.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 12,
              padding: '8px 0',
              borderBottom: '1px solid var(--ant-color-border-secondary)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
              <Package size={14} strokeWidth={1.75} />
              <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
                <Text style={{ fontSize: 13 }}>{a.belongingName ?? '—'}</Text>
                {a.assetModel ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>{a.assetModel}</Text>
                ) : null}
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {a.activationStatus ? (
                <StatusPill tone={ACTIVATION_STATUS_TONE[a.activationStatus]}>
                  {ACTIVATION_STATUS_LABEL[a.activationStatus]}
                </StatusPill>
              ) : null}
              <Button
                size="small"
                icon={<Eye size={14} />}
                onClick={() => setSelectedAsset(a)}
                aria-label="View asset details"
                style={{
                  background: 'linear-gradient(135deg, #e8f2ff 0%, #dbeafe 100%)',
                  borderColor: '#60a5fa',
                  color: '#2563eb',
                }}
              />
              <AssetActivationToggle row={a} />
            </div>
          </div>
        ))}
      </div>
      <AssetDetailModal
        asset={selectedAsset}
        open={!!selectedAsset}
        onClose={() => setSelectedAsset(null)}
      />
    </>
  );
}

function AssetActivationToggle({ row }: { row: BelongingIssuanceDto }) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const isActive = row.activationStatus !== 'INACTIVE';

  const mutation = useMutation({
    mutationFn: () => belongingIssuanceApi.updateActivation(row.id!, 'ACTIVE'),
    onSuccess: () => {
      message.success('Reactivated');
      queryClient.invalidateQueries({ queryKey: ['belonging-issuance'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  if (isActive) return null;

  return (
    <Popconfirm title="Reactivate this asset?" onConfirm={() => mutation.mutate()}>
      <Button size="small" loading={mutation.isPending}>
        Reactivate
      </Button>
    </Popconfirm>
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
  if (value === undefined || value === null || value === '') {
    return null;
  }
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

function formatDate(value: string | Date): string {
  const d = value instanceof Date ? value : new Date(value);
  return new Intl.DateTimeFormat('en-PK', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(d);
}

// Cheap type-only helper to keep ContractSummary's prop typed without re-importing.
type _Detail = Awaited<ReturnType<typeof peopleBff.detail>>;
function _useDetailContract(): _Detail['activeContract'] {
  return undefined;
}
void _useDetailContract;
