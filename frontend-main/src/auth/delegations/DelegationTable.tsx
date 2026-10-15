// Table for delegation rows. Same shape regardless of direction; the
// `kind` prop decides whether the leading column says "Delegate (from
// you)" or "Delegator (to you)" and whether the trailing actions column
// (Edit / Revoke) is rendered.

import {
  App as AntdApp,
  Button,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, XCircle } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import {
  DELEGATION_GRANT_LABEL,
  DELEGATION_STATUS_LABEL,
  DELEGATION_STATUS_TONE,
  delegationsApi,
  delegationsBff,
  formatDateMonthDay,
  type DelegationDto,
  type DelegationGrant,
  type DelegationStatus,
} from '@/attendance';
import { StatusPill } from '@/components';
import { getErrorMessage } from '@/lib';
import { useUserDisplay, useResolveUsers } from '@/chat/user-resolver';
import { REQUEST_TYPE_LABEL, type DelegationRequestType } from './request-types';

const { Text } = Typography;

/**
 * `/bff/users/by-ids` (the resolver behind `useUserDisplay`) doesn't return
 * employeeCode/Card No. Stopgap until that field lands on the backend: look
 * the name up against `/employees/delegation-candidates` (name-substring
 * search) and match by userId. Misses anyone outside that endpoint's scope
 * (inactive, no ESS, or the caller) — those rows just show the name.
 */
function useCardNo(userId: number, fullName: string | undefined) {
  const { data } = useQuery({
    queryKey: ['delegation-candidate-code', fullName],
    queryFn: ({ signal }) => delegationsBff.candidates({ q: fullName, limit: 5 }, signal),
    enabled: !!fullName,
    staleTime: 5 * 60_000,
    gcTime: 10 * 60_000,
  });
  return data?.users.find((u) => u.userId === userId)?.employeeCode ?? undefined;
}

function UserCell({ userId }: { userId: number }) {
  const display = useUserDisplay(userId);
  const name = display?.fullName ?? display?.username ?? `User #${userId}`;
  const code = useCardNo(userId, display?.fullName);

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <ArrowRight size={12} aria-hidden />
      <span style={{ fontWeight: 500 }}>
        {code ? `${name} · ${code}` : name}
      </span>
    </span>
  );
}

export type DelegationTableProps = {
  data: DelegationDto[];
  kind: 'OUTGOING' | 'INCOMING';
  onEdit: (d: DelegationDto) => void;
};

export function DelegationTable({ data, kind, onEdit }: DelegationTableProps) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const userIds = data.map((row) =>
    kind === 'OUTGOING' ? row.delegateId : row.delegatorId,
  );
  useResolveUsers(userIds);

  const revokeMutation = useMutation({
    mutationFn: (id: number) => delegationsApi.revoke(id),
    onSuccess: () => {
      message.success('Delegation revoked');
      queryClient.invalidateQueries({ queryKey: ['delegations'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const cols: TableColumnsType<DelegationDto> = [
    {
      title: kind === 'OUTGOING' ? 'Delegate (from you)' : 'Delegator (to you)',
      key: 'who',
      render: (_v, row) => (
        <UserCell
          userId={kind === 'OUTGOING' ? row.delegateId : row.delegatorId}
        />
      ),
    },
    {
      title: 'Grants',
      dataIndex: 'grants',
      key: 'grants',
      width: 150,
      render: (grants: DelegationGrant[] | null) => (
        <Space wrap size={[4, 4]}>
          {(grants && grants.length > 0 ? grants : (['APPROVE'] as DelegationGrant[])).map((g) => (
            <Tag key={g} color={g === 'APPROVE' ? 'geekblue' : 'purple'}>
              {DELEGATION_GRANT_LABEL[g] ?? g}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Request types',
      dataIndex: 'requestTypes',
      key: 'requestTypes',
      width: 280,
      render: (types: string[] | null) =>
        !types || types.length === 0 ? (
          <Tag>All types</Tag>
        ) : (
          <Space wrap size={[4, 4]}>
            {types.map((t) => (
              <Tag key={t} color="blue">
                {REQUEST_TYPE_LABEL[t as DelegationRequestType] ?? t}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: 'Range',
      key: 'range',
      width: 240,
      render: (_v, row) => (
        <span style={{ fontSize: 12 }}>
          {formatDateMonthDay(row.fromDate)}
          {' → '}
          {row.toDate ? (
            formatDateMonthDay(row.toDate)
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>
              open
            </Text>
          )}
        </span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (s: DelegationStatus, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <StatusPill tone={DELEGATION_STATUS_TONE[s]}>
            {DELEGATION_STATUS_LABEL[s]}
          </StatusPill>
          {row.revokedAt ? (
            <Text type="secondary" style={{ fontSize: 11 }}>
              {formatDateMonthDay(row.revokedAt)}
            </Text>
          ) : null}
        </span>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
      render: (r: string | null) => r ?? '—',
    },
  ];

  if (kind === 'OUTGOING') {
    cols.push({
      title: '',
      key: 'actions',
      width: 200,
      align: 'right',
      render: (_v, row) => {
        if (row.status !== 'ACTIVE') return null;
        return (
          <Space size={4}>
            <Button size="small" onClick={() => onEdit(row)}>
              Edit
            </Button>
            <Popconfirm
              title="Revoke this delegation?"
              description="In-flight requests fall back to you on the next inbox read. Past decisions are unaffected."
              okText="Revoke"
              okButtonProps={{ danger: true }}
              onConfirm={() => revokeMutation.mutate(row.id)}
            >
              <Button size="small" danger icon={<XCircle size={12} />}>
                Revoke
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    });
  }

  return (
    <Table
      rowKey="id"
      dataSource={data}
      columns={cols}
      size="middle"
      pagination={{ pageSize: 20, hideOnSinglePage: true }}
      // Scroll wide columns inside the table, not the page.
      scroll={{ x: 'max-content' }}
    />
  );
}
