import { Table, Typography, type TableProps } from 'antd';

import { PersonCell, StatusPill } from '@/components';
import { formatDateShort } from '@/lib/dates';
import type { EmployeeDto } from '@/people';
import { ACTIVATION_STATUS_LABEL, ACTIVATION_STATUS_TONE, CLEARANCE_STATUS_LABEL, CLEARANCE_STATUS_TONE } from '../format';
import type { BelongingIssuanceDto } from '../types';
import { BelongingIssuanceRowActions } from './BelongingIssuanceRowActions';

const { Text } = Typography;

export type BelongingIssuanceTableProps = {
  data: BelongingIssuanceDto[];
  loading: boolean;
  employeeById?: Map<number, EmployeeDto>;
  onEdit: (row: BelongingIssuanceDto) => void;
  pagination?: TableProps<BelongingIssuanceDto>['pagination'];
  /** Hide the Employee column — for single-employee views where it's redundant. */
  hideEmployeeColumn?: boolean;
};

export function BelongingIssuanceTable({
  data,
  loading,
  employeeById,
  onEdit,
  pagination,
  hideEmployeeColumn,
}: BelongingIssuanceTableProps) {
  const cols: TableProps<BelongingIssuanceDto>['columns'] = [
    {
      title: 'Asset Type',
      key: 'belongingName',
      render: (_v, row) => row.belongingName ?? `#${row.belongingMasterId ?? '—'}`,
    },
    ...(hideEmployeeColumn
      ? []
      : [
          {
            title: 'Employee',
            key: 'employeeId',
            render: (_v: unknown, row: BelongingIssuanceDto) => {
              const e = row.employeeId != null ? employeeById?.get(row.employeeId) : undefined;
              if (!e) return <Text type="secondary">{row.employeeId ? `#${row.employeeId}` : '—'}</Text>;
              return (
                <PersonCell
                  size="compact"
                  person={{
                    fullName: e.fullName ?? '—',
                    employeeCode: e.employeeCode,
                    profilePictureUrl: e.profilePictureUrl,
                  }}
                />
              );
            },
          },
        ]),
    {
      title: 'Asset',
      key: 'asset',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <span>{row.assetModel ?? '—'}</span>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {[row.assetYear, row.condition].filter(Boolean).join(' · ') || '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Issued',
      key: 'issuedDate',
      render: (_v, row) => (row.issuedDate ? formatDateShort(row.issuedDate) : '—'),
    },
    {
      title: 'Expiry',
      key: 'expiryDate',
      render: (_v, row) => (row.expiryDate ? formatDateShort(row.expiryDate) : '—'),
    },
    {
      title: 'Activation',
      key: 'activationStatus',
      render: (_v, row) =>
        row.activationStatus ? (
          <StatusPill tone={ACTIVATION_STATUS_TONE[row.activationStatus]}>
            {ACTIVATION_STATUS_LABEL[row.activationStatus]}
          </StatusPill>
        ) : (
          '—'
        ),
    },
    {
      title: 'Clearance',
      key: 'clearanceStatus',
      render: (_v, row) =>
        row.clearanceStatus ? (
          <StatusPill tone={CLEARANCE_STATUS_TONE[row.clearanceStatus]}>
            {CLEARANCE_STATUS_LABEL[row.clearanceStatus]}
          </StatusPill>
        ) : (
          '—'
        ),
    },
    {
      title: '',
      key: 'rowActions',
      width: 260,
      align: 'right',
      render: (_v, row) => <BelongingIssuanceRowActions row={row} onEdit={onEdit} />,
    },
  ];

  return (
    <Table<BelongingIssuanceDto>
      rowKey={(r) => r.id ?? Math.random()}
      columns={cols}
      dataSource={data}
      loading={loading}
      size="middle"
      scroll={{ x: 'max-content' }}
      pagination={pagination}
    />
  );
}
